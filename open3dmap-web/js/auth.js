// Authentication Manager for Firebase v11+
// Manages user authentication state and redirection.

class AuthManager {
    constructor() {
        this.auth = null;
        this.firestore = null;
        this.currentUser = null;
        this.isRegistering = false;
        this.loginModal = null;
        this.emailForm = null;
        this.hasProcessedInitialAuth = false;

        this.initPromise = this.deferredInit();
    }

    async deferredInit() {
        await this.waitForDOM();
        await this.waitForFirebase();
        
        // This class is used on both index.html and dashboard.html.
        // The login modal only exists on index.html.
        this.loginModal = document.getElementById('login-modal');
        this.emailForm = document.getElementById('email-form');

        this.init();
    }

    async waitForDOM() {
        return new Promise(resolve => {
            if (document.readyState === 'loading') {
                document.addEventListener('DOMContentLoaded', resolve, { once: true });
            } else {
                resolve();
            }
        });
    }

    async waitForFirebase() {
        return new Promise((resolve) => {
            const setupFirebase = () => {
                if (window.firebaseServices) {
                    this.auth = window.firebaseServices.auth;
                    this.firestore = window.firebaseServices.firestore;
                    window.removeEventListener('firebaseReady', setupFirebase);
                    console.log('AuthManager: Firebase services are ready.');
                    resolve();
                }
            };

            if (window.firebaseServices) {
                setupFirebase();
            } else {
                window.addEventListener('firebaseReady', setupFirebase, { once: true });
            }
        });
    }

    init() {
        if (!this.auth) {
            console.error('Auth service is not available for init.');
            return;
        }
        const { onAuthStateChanged, getRedirectResult } = window.firebaseModules;
        
        // Only check for redirect result on the main login page
        if (this.isLoginPage()) {
            // Check for redirect result (in case popup fallback was used)
            this.showLoading(true);
            getRedirectResult(this.auth)
                .then((result) => {
                    if (result) {
                        // This will trigger the onAuthStateChanged listener, which handles the redirect.
                        console.log('Redirect sign-in successful:', result.user.email);
                        console.log('User details:', {
                            uid: result.user.uid,
                            email: result.user.email,
                            displayName: result.user.displayName,
                            photoURL: result.user.photoURL
                        });
                        this.showToast(`Welcome ${result.user.displayName || result.user.email}!`, 'success');
                    } else {
                        console.log('No redirect result found');
                    }
                })
                .catch((error) => {
                    console.error('Error getting redirect result:', error);
                    console.error('Error code:', error.code);
                    console.error('Error message:', error.message);
                    this.handleAuthError(error);
                })
                .finally(() => {
                    this.showLoading(false);
                });
        }

        onAuthStateChanged(this.auth, (user) => {
            this.handleAuthStateChange(user);
        });

        this.setupEventListeners();
        console.log('AuthManager initialized.');
    }

    handleAuthStateChange(user) {
        console.log('Auth state changed:', user ? 'User signed in' : 'User signed out');
        console.log('Current page:', window.location.pathname);
        console.log('Is login page?', this.isLoginPage());
        
        this.currentUser = user;
        
        if (user) {
            // User is signed in.
            console.log('User signed in, announcing auth ready...');
            window.dispatchEvent(new CustomEvent('authReady'));

            if (this.isLoginPage()) {
                // Check if user just signed in (redirect to dashboard) vs navigated to home page manually
                const shouldRedirectToDashboard = this.shouldRedirectAfterLogin();
                
                if (shouldRedirectToDashboard) {
                    console.log('User just signed in, redirecting to dashboard...');
                    window.location.href = 'dashboard.html';
                    return; // Exit early since we're redirecting
                } else {
                    console.log('User navigated to home page, staying on home page and updating UI...');
                    // User is on home page but already logged in - update UI to show logged-in state
                    this.hideLoginModal(); // Hide the login modal if it's open
                    this.updateAuthButton(true);
                    this.updateUserNavigation({
                        displayName: user.displayName,
                        email: user.email,
                        photoUrl: user.photoURL
                    });
                    console.log('UI updated for logged-in user on home page');
                }
            } else {
                console.log('On dashboard page, updating UI...');
                // If on dashboard, update the UI.
                this.updateAuthButton(true);
                this.updateUserNavigation({
                    displayName: user.displayName,
                    email: user.email,
                    photoUrl: user.photoURL
                });
            }
        } else {
            // User is signed out.
            console.log('User signed out');
            // If on the dashboard, redirect to the login page.
            if (!this.isLoginPage()) {
                console.log('On dashboard page, redirecting to login...');
                window.location.href = 'index.html';
                return; // Exit early since we're redirecting
            } else {
                console.log('On login page, updating UI...');
                this.updateAuthButton(false);
            }
        }
    }

    setupEventListeners() {
        // Event listeners for the login modal on index.html
        if (this.loginModal) {
            document.querySelectorAll('[data-action="show-login"]').forEach(button => {
                button.addEventListener('click', () => this.showLoginModal());
            });

            const closeButton = this.loginModal.querySelector('.modal-close');
            if (closeButton) {
                closeButton.addEventListener('click', () => this.hideLoginModal());
            }

            this.loginModal.addEventListener('click', (e) => {
                if (e.target === this.loginModal) {
                    this.hideLoginModal();
                }
            });

            const googleBtn = document.getElementById('google-signin');
            if (googleBtn) googleBtn.addEventListener('click', () => this.signInWithGoogle());

            const githubBtn = document.getElementById('github-signin');
            if (githubBtn) githubBtn.addEventListener('click', () => this.signInWithGithub());
            
            const emailBtn = document.getElementById('email-signin');
            if (emailBtn) emailBtn.addEventListener('click', () => this.toggleEmailForm());
            
            const emailLoginForm = document.getElementById('email-login-form');
            if (emailLoginForm) emailLoginForm.addEventListener('submit', (e) => {
                e.preventDefault();
                this.handleEmailLogin(e.target);
            });
        }

        // Event listeners for the user navigation on dashboard.html
        const authBtn = document.getElementById('auth-btn');
        if (authBtn && !this.currentUser) {
            authBtn.addEventListener('click', () => this.showLoginModal());
        }

        const userNavSignout = document.getElementById('user-nav-signout');
        if (userNavSignout) {
            userNavSignout.addEventListener('click', () => this.signOut());
        }

        this.setupUserNavigation();
    }

    isLoginPage() {
        const pathname = window.location.pathname;
        const isLogin = pathname === '/' || pathname === '/index.html' || pathname.endsWith('/') || pathname.endsWith('/index.html');
        console.log('isLoginPage check:', { pathname, isLogin });
        return isLogin;
    }

    shouldRedirectAfterLogin() {
        // Check if this is a fresh authentication or user navigating to home
        const urlParams = new URLSearchParams(window.location.search);
        const hasAuthParams = urlParams.has('code') || urlParams.has('state'); // OAuth redirect params
        const referrer = document.referrer;
        const isFromDashboard = referrer.includes('dashboard.html');
        
        console.log('Redirect logic - referrer:', referrer, 'isFromDashboard:', isFromDashboard, 'hasProcessedInitialAuth:', this.hasProcessedInitialAuth);
        
        // If there are OAuth parameters, this is definitely a redirect flow
        if (hasAuthParams) {
            console.log('OAuth redirect detected, will redirect to dashboard');
            return true;
        }
        
        // For the first auth state change on page load
        if (!this.hasProcessedInitialAuth) {
            this.hasProcessedInitialAuth = true;
            
            // If user came from dashboard and is already logged in, don't redirect
            // (they clicked "Home" button while already logged in)
            if (isFromDashboard) {
                console.log('User navigated from dashboard while already logged in, staying on home page');
                return false;
            }
            
            // Otherwise, this is likely a fresh login or page refresh with existing auth
            // Since we want to redirect after login, redirect to dashboard
            console.log('Initial auth state change, redirecting to dashboard');
            return true;
        }
        
        // Subsequent auth state changes (user clicking sign in) should always redirect
        console.log('Subsequent sign-in event, will redirect to dashboard');
        return true;
    }

    async signOut() {
        const { signOut } = window.firebaseModules;
        try {
            await signOut(this.auth);
            console.log('User signed out successfully.');
            // onAuthStateChanged will handle the redirect.
        } catch (error) {
            console.error('Sign out error:', error);
            this.showToast('Failed to sign out.', 'error');
        }
    }

    updateAuthButton(isSignedIn) {
        console.log('updateAuthButton called with isSignedIn:', isSignedIn);
        
        const authBtn = document.getElementById('auth-btn');
        const userNavDropdown = document.getElementById('user-nav-dropdown');

        console.log('Found elements - authBtn:', !!authBtn, 'userNavDropdown:', !!userNavDropdown);

        if (authBtn && userNavDropdown) {
            if (isSignedIn) {
                authBtn.style.display = 'none';
                userNavDropdown.style.display = 'block';
                console.log('UI updated: auth button hidden, user nav shown');
            } else {
                authBtn.style.display = 'block';
                userNavDropdown.style.display = 'none';
                console.log('UI updated: auth button shown, user nav hidden');
            }
        } else {
            console.warn('Could not find auth button or user nav dropdown elements');
        }

        // Update hero section on home page
        if (this.isLoginPage()) {
            this.updateHeroElements(isSignedIn);
        }
    }

    async updateHeroElements(isSignedIn, retryCount = 0) {
        const maxRetries = 3;
        const retryDelay = 100; // milliseconds
        
        const heroActionsGuest = document.getElementById('hero-actions-guest');
        const heroActionsUser = document.getElementById('hero-actions-user');

        console.log('Found hero elements - guest:', !!heroActionsGuest, 'user:', !!heroActionsUser);

        if (heroActionsGuest && heroActionsUser) {
            if (isSignedIn) {
                heroActionsGuest.style.display = 'none';
                heroActionsUser.style.display = 'flex';
                console.log('Hero updated: guest actions hidden, user actions shown');
            } else {
                heroActionsGuest.style.display = 'flex';
                heroActionsUser.style.display = 'none';
                console.log('Hero updated: guest actions shown, user actions hidden');
            }
        } else if (retryCount < maxRetries) {
            console.log(`Hero elements not found, retrying in ${retryDelay}ms (attempt ${retryCount + 1}/${maxRetries})`);
            setTimeout(() => {
                this.updateHeroElements(isSignedIn, retryCount + 1);
            }, retryDelay);
        } else {
            console.warn('Could not find hero action elements after', maxRetries, 'attempts');
        }
    }

    async updateUserNavigation(userData) {
        if (!this.currentUser) return;

        const userElements = {
            avatar: document.getElementById('user-nav-avatar'),
            name: document.getElementById('user-nav-name'),
            headerAvatar: document.getElementById('user-nav-header-avatar'),
            displayName: document.getElementById('user-nav-display-name'),
            email: document.getElementById('user-nav-email'),
        };

        if (userElements.avatar) userElements.avatar.src = userData.photoUrl || 'assets/default-avatar.png';
        if (userElements.name) userElements.name.textContent = userData.displayName || 'User';
        if (userElements.headerAvatar) userElements.headerAvatar.src = userData.photoUrl || 'assets/default-avatar.png';
        if (userElements.displayName) userElements.displayName.textContent = userData.displayName || 'User';
        if (userElements.email) userElements.email.textContent = userData.email;
    }

    setupUserNavigation() {
        const userNavTrigger = document.getElementById('user-nav-trigger');
        const userNavDropdown = document.getElementById('user-nav-dropdown');

        if (userNavTrigger && userNavDropdown) {
            userNavTrigger.addEventListener('click', (e) => {
                e.stopPropagation();
                userNavDropdown.classList.toggle('open');
            });

            document.addEventListener('click', (e) => {
                if (!userNavDropdown.contains(e.target)) {
                    userNavDropdown.classList.remove('open');
                }
            });
        }
    }

    // --- All functions below this line are for the login modal ---

    showLoginModal() {
        if (this.loginModal) {
            this.loginModal.classList.add('active');
        }
    }

    hideLoginModal() {
        if (this.loginModal) {
            this.loginModal.classList.remove('active');
        }
    }

    toggleEmailForm() {
        const authButtons = this.loginModal.querySelector('.auth-buttons');
        if (this.emailForm && authButtons) {
            const isVisible = this.emailForm.style.display !== 'none';
            this.emailForm.style.display = isVisible ? 'none' : 'block';
            authButtons.style.display = isVisible ? 'block' : 'none';
        }
    }

    async signInWithGoogle() {
        const { GoogleAuthProvider, signInWithPopup, signInWithRedirect } = window.firebaseModules;
        try {
            this.showLoading(true);
            const provider = new GoogleAuthProvider();
            provider.setCustomParameters({ prompt: 'select_account' });
            
            // Add additional scopes if needed
            provider.addScope('profile');
            provider.addScope('email');
            
            try {
                await signInWithPopup(this.auth, provider);
            } catch (error) {
                if (error.code === 'auth/popup-blocked') {
                    // If popup is blocked, try redirect
                    await signInWithRedirect(this.auth, provider);
                } else {
                    throw error;
                }
            }
        } catch (error) {
            console.error('Google sign-in error:', error);
            
            // Provide more specific error messages
            if (error.code === 'auth/cancelled-popup-request') {
                console.warn('Sign-in popup was cancelled or blocked');
            } else if (error.code === 'auth/popup-closed-by-user') {
                console.warn('Sign-in popup was closed by user');
            } else if (error.code === 'auth/unauthorized-domain') {
                console.error('Domain not authorized for OAuth. Please add your domain to Firebase console.');
            }
            
            this.handleAuthError(error);
        } finally {
            this.showLoading(false);
        }
    }

    async signInWithGithub() {
        const { GithubAuthProvider, signInWithPopup, signInWithRedirect } = window.firebaseModules;
        try {
            this.showLoading(true);
            const provider = new GithubAuthProvider();
            provider.setCustomParameters({ prompt: 'select_account' });
            
            try {
                await signInWithPopup(this.auth, provider);
            } catch (error) {
                if (error.code === 'auth/popup-blocked') {
                    // If popup is blocked, try redirect
                    await signInWithRedirect(this.auth, provider);
                } else {
                    throw error;
                }
            }
        } catch (error) {
            console.error('Github sign-in error:', error);
            this.handleAuthError(error);
        } finally {
            this.showLoading(false);
        }
    }

    async handleEmailLogin(form) {
        const { signInWithEmailAndPassword } = window.firebaseModules;
        const email = form.email.value;
        const password = form.password.value;
        
        try {
            this.showLoading(true);
            await signInWithEmailAndPassword(this.auth, email, password);
        } catch (error) {
            console.error('Email sign-in error:', error);
            this.handleAuthError(error);
        } finally {
            this.showLoading(false);
        }
    }
    
    handleAuthError(error) {
        console.error("Authentication error:", error);
        console.error("Error details:", {
            code: error.code,
            message: error.message,
            stack: error.stack
        });
        
        let message = 'An unknown error occurred.';
        switch (error.code) {
            case 'auth/user-not-found':
                message = 'No account found with this email.';
                break;
            case 'auth/wrong-password':
            case 'auth/invalid-credential':
                message = 'Incorrect email or password. Please check your credentials and try again.';
                break;
            case 'auth/popup-blocked':
                message = 'Popup blocked by browser. Please allow popups.';
                break;
            case 'auth/cancelled-popup-request':
            case 'auth/popup-closed-by-user':
                return; // Don't show a message for user-cancelled popups
            case 'auth/account-exists-with-different-credential':
                message = 'An account with this email already exists using a different sign-in method. Please use the same method you used to create your account (Google, GitHub, or Email).';
                break;
            case 'auth/unauthorized-domain':
                message = 'This domain is not authorized for OAuth operations. Please contact support.';
                break;
            case 'auth/operation-not-allowed':
                message = 'This sign-in method is not enabled. Please contact support.';
                break;
            case 'auth/configuration-not-found':
                message = 'Firebase configuration error. Please contact support.';
                break;
            default:
                message = `Authentication failed: ${error.message}`;
        }
        this.showToast(message, 'error');
    }

    showLoading(show) {
        const overlay = document.getElementById('loading-overlay');
        if (overlay) {
            overlay.style.display = show ? 'flex' : 'none';
        }
    }

    showToast(message, type = 'info') {
        const container = document.getElementById('toast-container');
        if (!container) return;
        const toast = document.createElement('div');
        toast.className = `toast toast-${type}`;
        toast.textContent = message;
        container.appendChild(toast);
        setTimeout(() => toast.remove(), 5000);
    }
}

// Instantiate the AuthManager and attach it to the window
if (!window.authManager) {
    window.authManager = new AuthManager();
} 