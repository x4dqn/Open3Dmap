package com.example.arcore_scanner.data.database

import androidx.room.TypeConverter
import com.google.ar.core.Pose
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.example.arcore_scanner.data.models.GpsLocation
import com.example.arcore_scanner.data.models.Frame.ImuData
import com.example.arcore_scanner.data.models.Frame.ExposureInfo
import com.example.arcore_scanner.data.models.CameraIntrinsics

class Converters {
    private val gson = Gson()

    @TypeConverter
    fun fromPose(pose: Pose): String {
        val translation = FloatArray(3)
        val rotation = FloatArray(4)
        pose.getTranslation(translation, 0)
        pose.getRotationQuaternion(rotation, 0)
        
        // Create a simple map with string keys and float array values
        val poseMap = mapOf(
            "t" to translation.toList(),
            "r" to rotation.toList()
        )
        return gson.toJson(poseMap)
    }

    @TypeConverter
    fun toPose(value: String): Pose {
        val type = object : TypeToken<Map<String, List<Float>>>() {}.type
        val map: Map<String, List<Float>> = gson.fromJson(value, type)
        
        // Convert lists back to float arrays
        val translation = map["t"]?.toFloatArray() ?: FloatArray(3)
        val rotation = map["r"]?.toFloatArray() ?: FloatArray(4)
        
        return Pose(translation, rotation)
    }

    @TypeConverter
    fun fromFloatArray(value: FloatArray): String = gson.toJson(value.toList())

    @TypeConverter
    fun toFloatArray(value: String): FloatArray {
        val type = object : TypeToken<List<Float>>() {}.type
        val list: List<Float> = gson.fromJson(value, type)
        return list.toFloatArray()
    }

    @TypeConverter
    fun fromGpsLocation(location: GpsLocation): String = gson.toJson(location)

    @TypeConverter
    fun toGpsLocation(value: String): GpsLocation = gson.fromJson(value, GpsLocation::class.java)

    @TypeConverter
    fun fromImuData(data: ImuData): String = gson.toJson(data)

    @TypeConverter
    fun toImuData(value: String): ImuData = gson.fromJson(value, ImuData::class.java)

    @TypeConverter
    fun fromExposureInfo(info: ExposureInfo): String = gson.toJson(info)

    @TypeConverter
    fun toExposureInfo(value: String): ExposureInfo = gson.fromJson(value, ExposureInfo::class.java)

    @TypeConverter
    fun fromCameraIntrinsics(intrinsics: CameraIntrinsics): String = gson.toJson(intrinsics)

    @TypeConverter
    fun toCameraIntrinsics(value: String): CameraIntrinsics = gson.fromJson(value, CameraIntrinsics::class.java)

    @TypeConverter
    fun fromStringList(value: List<String>): String = gson.toJson(value)

    @TypeConverter
    fun toStringList(value: String): List<String> {
        val type = object : TypeToken<List<String>>() {}.type
        return gson.fromJson(value, type)
    }
} 