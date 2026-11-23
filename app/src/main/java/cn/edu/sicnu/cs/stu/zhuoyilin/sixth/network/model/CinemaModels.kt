package cn.edu.sicnu.cs.stu.zhuoyilin.sixth.network.model

import com.google.gson.annotations.SerializedName

data class Cinema(
    @SerializedName(value = "cinemaId", alternate = ["id"])
    val cinemaId: Int = 0,
    @SerializedName(value = "name", alternate = ["cinemaName"])
    val name: String = "",
    @SerializedName(value = "address", alternate = ["location"])
    val address: String = ""
)

data class Schedule(
    @SerializedName(value = "scheduleId", alternate = ["id"])
    val scheduleId: Int = 0,
    @SerializedName(value = "cinemaId")
    val cinemaId: Int = 0,
    @SerializedName(value = "startTime", alternate = ["showTime", "start_time"])
    val startTime: String = "",
    @SerializedName(value = "endTime", alternate = ["end_time"])
    val endTime: String = "",
    @SerializedName(value = "price", alternate = ["ticketPrice"])
    val price: Double = 0.0,
    @SerializedName(value = "hallName", alternate = ["hall"])
    val hallName: String = "",
    @SerializedName(value = "remainingSeats", alternate = ["remainSeats", "availableSeats"])
    val remainingSeats: Int = -1,
    @SerializedName(value = "totalSeats", alternate = ["seatCount"])
    val totalSeats: Int = 0,
    @SerializedName(value = "soldOut", alternate = ["isSoldOut"])
    val soldOut: Boolean = false
) {
    val isSoldOut: Boolean
        get() = soldOut || remainingSeats == 0
}
