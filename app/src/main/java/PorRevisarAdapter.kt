import android.os.Parcel
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.larangel.rondingpeinn.R
import java.time.LocalDateTime

data class PorRevisarRecord(val street: String, val number: String, var time: LocalDateTime, var parkingSlotKey: String, val validation: String, var latitude: Double, var longitude: Double) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readString()!!,
        parcel.readString()!!,
        LocalDateTime.parse(parcel.readString()),
        parcel.readString()!!,
        parcel.readString()!!,
        parcel.readDouble(),
        parcel.readDouble()
    ) {}

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(street)
        parcel.writeString(number)
        parcel.writeString(time.toString())
        parcel.writeString(parkingSlotKey)
        parcel.writeString(validation)
        parcel.writeDouble(latitude)
        parcel.writeDouble(longitude)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<PorRevisarRecord> {
        override fun createFromParcel(parcel: Parcel): PorRevisarRecord {
            return PorRevisarRecord(parcel)
        }

        override fun newArray(size: Int): Array<PorRevisarRecord?> {
            return arrayOfNulls(size)
        }
    }
}

class PorRevisarAdapter(
    private var records: List<PorRevisarRecord>,
    private val onClick: (PorRevisarRecord) -> Unit
) : RecyclerView.Adapter<PorRevisarAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val streetText: TextView = view.findViewById(R.id.streetText)
        val numberText: TextView = view.findViewById(R.id.numberText)
        val timeText: TextView = view.findViewById(R.id.timeText)
        val slotText: TextView = view.findViewById(R.id.slotText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_por_revisar, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val record = records[position]
        holder.streetText.text = "Street: ${record.street}"
        holder.numberText.text = "Number: ${record.number}"
        holder.timeText.text = "Time: ${record.time}"
        holder.slotText.text = "Slot: ${record.parkingSlotKey}"
        holder.itemView.setOnClickListener { onClick(record) }
    }

    override fun getItemCount(): Int = records.size

    fun updateRecords(newRecords: List<PorRevisarRecord>) {
        records = newRecords
        notifyDataSetChanged()
    }
}