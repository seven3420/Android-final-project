package cn.edu.sicnu.cs.stu.zhuoyilin.sixth.network.model

import com.google.gson.annotations.SerializedName

/**
 * 座位状态约定：0 可选，1 已售，2 已锁定。若后端定义不同，改这里即可。
 */
data class Seat(
    @SerializedName(value = "seatId", alternate = ["id"])
    val seatId: Int = 0,
    @SerializedName(value = "row", alternate = ["rowNum", "rowIndex"])
    val row: Int = 0,
    @SerializedName(value = "col", alternate = ["column", "colIndex", "columnNum"])
    val col: Int = 0,
    @SerializedName(value = "status", alternate = ["state", "seatStatus"])
    val status: Int = 0,
    @SerializedName(value = "seatNo", alternate = ["name", "label"])
    val seatNo: String = "",
    @SerializedName(value = "price", alternate = ["ticketPrice", "seatPrice"])
    val price: Double = 0.0
) {
    val isAvailable: Boolean get() = status == 0
    val isSold: Boolean get() = status == 1
    val isLocked: Boolean get() = status == 2

    val key: String
        get() = if (seatId != 0) "id-$seatId" else "pos-$row-$col"

    val label: String
        get() = seatNo.ifBlank { "${row}排${col}座" }
}

data class SeatMap(
    @SerializedName(value = "rows", alternate = ["rowCount"])
    val rows: Int = 0,
    @SerializedName(value = "cols", alternate = ["columnCount", "columns"])
    val cols: Int = 0,
    val price: Double = 0.0,
    @SerializedName(value = "seats", alternate = ["seatList", "list"])
    val seats: List<Seat> = emptyList()
)
