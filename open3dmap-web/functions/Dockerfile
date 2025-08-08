# Use the official COLMAP Docker image as base
FROM colmap/colmap:latest

# Install Node.js and npm
RUN apt-get update && apt-get install -y \
    curl \
    && curl -fsSL https://deb.nodesource.com/setup_20.x | bash - \
    && apt-get install -y nodejs \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

# Set working directory
WORKDIR /workspace

# Verify COLMAP installation
RUN colmap help

# Copy function source
COPY package*.json ./
RUN npm install --omit=dev

COPY . .

# Set environment variables
ENV NODE_ENV=production
ENV PORT=8080

# Expose the port that the HTTP server will use
EXPOSE 8080

# Start the HTTP server
CMD ["node", "server.js"] 