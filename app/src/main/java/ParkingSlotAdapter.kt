import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.larangel.rondingpeinn.R

data class ParkingSlot(
    val latitude: Double,
    val longitude: Double,
    val key: String)
data class ParkingSlotWithRow(
    val slot: ParkingSlot,
    val row: Int)

class ParkingSlotAdapter(
    private val slots: List<ParkingSlotWithRow>,
    private val onEdit: (ParkingSlotWithRow) -> Unit,
    private val onDelete: (ParkingSlotWithRow) -> Unit
) : RecyclerView.Adapter<ParkingSlotAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val latText: TextView = view.findViewById(R.id.latText)
        val lonText: TextView = view.findViewById(R.id.lonText)
        val keyText: TextView = view.findViewById(R.id.keyText)
        val editButton: Button = view.findViewById(R.id.editButton)
        val deleteButton: Button = view.findViewById(R.id.deleteButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_parkingslot, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val slotWithRow = slots[position]
        val slot = slotWithRow.slot
        holder.latText.text = "Lat: ${String.format("%.6f", slot.latitude)}"
        holder.lonText.text = "Lon: ${String.format("%.6f", slot.longitude)}"
        holder.keyText.text = "Key: ${slot.key}"
        holder.editButton.setOnClickListener { onEdit(slotWithRow) }
        holder.deleteButton.setOnClickListener { onDelete(slotWithRow) }
    }

    override fun getItemCount(): Int = slots.size
}