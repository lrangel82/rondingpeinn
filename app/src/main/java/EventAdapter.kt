import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.larangel.rondingpeinn.R
import java.time.LocalDate
import java.time.LocalDateTime
import com.bumptech.glide.Glide
import java.io.File
import java.time.format.DateTimeFormatter

data class EventModal (
    var plate: String,
    var date: LocalDate,
    var time: LocalDateTime,
    val localPhotoPath: String, // Local file path if Drive upload fails
    var parkingSlotKey: String
)
class EventAdapter(private val events: ArrayList<EventModal>) : RecyclerView.Adapter<EventAdapter.EventViewHolder>() {

    class EventViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val plateText: TextView = itemView.findViewById(R.id.eventPlateText)
        val dateText: TextView = itemView.findViewById(R.id.eventDateText)
        val timeText: TextView = itemView.findViewById(R.id.eventTimeText)
        val parkingSlotKeyText: TextView = itemView.findViewById(R.id.eventParkingSlotKeyText)
        val eventThumbnail: ImageView = itemView.findViewById(R.id.eventThumbnail)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_event, parent, false)
        return EventViewHolder(view)
    }

    override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
        val event = events[position]
        holder.plateText.text = "Plate: ${event.plate}"
        holder.dateText.text = "Date: ${event.date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))}"
        holder.timeText.text = "Time: ${event.time.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}"
        holder.parkingSlotKeyText.text = "Parking Slot: ${event.parkingSlotKey}"
        // Load thumbnail from localPhotoPath
        if (event.localPhotoPath.isNotEmpty()) {
            val file = File(event.localPhotoPath)
            if (file.exists()) {
                Glide.with(holder.itemView.context)
                    .load(file)
                    .placeholder(R.drawable.placeholder_image)
                    .error(R.drawable.error_image)
                    .into(holder.eventThumbnail)
            } else {
                holder.eventThumbnail.setImageResource(R.drawable.placeholder_image)
            }
        } else {
            holder.eventThumbnail.setImageResource(R.drawable.placeholder_image)
        }
    }

    override fun getItemCount(): Int {
        return events.size
    }
}