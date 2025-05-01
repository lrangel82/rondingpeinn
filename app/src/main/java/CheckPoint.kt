import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.larangel.rondingpeinn.R

data class CheckPoint(val identificador: String, val latitud: Double, val longitud: Double, var escanedo: Boolean)

class CheckPointAdapter(
    private val dataList: List<CheckPoint>,
    private val onItemClick: (CheckPoint) -> Unit) : RecyclerView.Adapter<CheckPointAdapter.CheckPointViewHolder>() {

    class CheckPointViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val nameTextView: TextView = itemView.findViewById(R.id.nameTextView)
        val descriptionTextView: TextView = itemView.findViewById(R.id.descriptionTextView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CheckPointViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_checkpoint, parent, false)
        return CheckPointViewHolder(view)
    }

    override fun onBindViewHolder(holder: CheckPointViewHolder, position: Int) {
        val myCheckPoint = dataList[position]
        holder.nameTextView.text = myCheckPoint.identificador
        val desc="lat:"+myCheckPoint.latitud.toString() + ", lon:"+myCheckPoint.longitud.toString()
        holder.descriptionTextView.text = desc
        // Set click listener for the item view
        holder.itemView.setOnClickListener {
            onItemClick(myCheckPoint)
        }
    }

    override fun getItemCount(): Int {
        return dataList.size
    }
}