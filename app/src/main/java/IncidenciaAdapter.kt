import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.larangel.rondingpeinn.R
import java.io.File

class IncidenciaAdapter(
    private val incidencias: List<List<Any>>,
    private val onClick: (List<Any>) -> Unit
): RecyclerView.Adapter<IncidenciaAdapter.ViewHolder>() {

    class ViewHolder(itemView: View): RecyclerView.ViewHolder(itemView) {
        val imgThumbnail: ImageView = itemView.findViewById(R.id.imgThumbnail)
        val txtCalleNumero: TextView = itemView.findViewById(R.id.txtCalleNumero)
        val txtFechaHora: TextView = itemView.findViewById(R.id.txtFechaHora)
        val txtDescripcion: TextView = itemView.findViewById(R.id.txtDescripcion)

    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_incidencia, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount() = incidencias.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val row = incidencias[position]
        // row: [calle, numero, date, datetime, tipo, photoPath, descripcion]
        holder.txtCalleNumero.text = "${row[0]}:${row[1]}"
        holder.txtFechaHora.text = row[3].toString()
        holder.txtDescripcion.text = row[6].toString()

        // Mostrar imagen como thumbnail, si existe
        val photoPath = row[5] as? String
        if (!photoPath.isNullOrEmpty()) {
            val file = File(photoPath)
            if (file.exists()) {
                holder.imgThumbnail.setImageBitmap(BitmapFactory.decodeFile(photoPath))
            } else {
                holder.imgThumbnail.setImageResource(R.drawable.error_image) // Asegúrate de tener este recurso
            }
        } else {
            holder.imgThumbnail.setImageResource(R.drawable.error_image)
        }
        //On click
        holder.itemView.setOnClickListener { onClick(row) }
    }
}