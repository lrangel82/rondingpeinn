import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.recyclerview.widget.RecyclerView
import com.larangel.rondy.R

class VehiculoAdapter (
    private var items: List<List<String>>,
    private val onSelected: (List<String>) -> Unit
) : RecyclerView.Adapter<VehiculoAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val button: Button = view.findViewById(R.id.btnItemVehicle)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_vehiculo, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val v = items[position]
        // Formato: [placas, calle, numero, marca, modelo, color, tag]
        // Texto deseado: placa-calle:numero-marca-tag
        val displayPath = "${v.getOrNull(0)} - ${v.getOrNull(1)?.substring(0,3)?.uppercase()}:${v.getOrNull(2)} - ${v.getOrNull(3)} - ${v.getOrNull(6)}"

        holder.button.text = displayPath
        holder.button.setOnClickListener { onSelected(v) }
    }

    override fun getItemCount() = items.size

    fun updateData(newItems: List<List<String>>) {
        items = newItems
        notifyDataSetChanged()
    }
}