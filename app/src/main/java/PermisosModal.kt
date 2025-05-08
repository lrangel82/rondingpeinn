import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.larangel.rondingpeinn.R
import java.time.LocalDate


data class PermisosModal (
    var domicilio: String,
    var tipo: String,
    var fechaInicio: LocalDate,
    var fechaFin: LocalDate,
    var descripcion: String,
    var aprobado: Boolean,
    var procesado: Boolean
)
data class SheetRow(
    val cells: List<String>
)

class PermisosRVAdapter(
    private val permisosModalArrayList: ArrayList<PermisosModal>
    //private val context: Context
) : RecyclerView.Adapter<PermisosRVAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {

        // Inflating our layout file.
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_permisos, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {

        // Getting data from our array
        // list in our modal class.
        val permisosModal = permisosModalArrayList[position]

        // Setting data to our text views.
        holder.domicilioTV.text = permisosModal.domicilio
        holder.fecha_inicioTV.text = permisosModal.fechaInicio.toString()
        holder.fecha_finTV.text = permisosModal.fechaFin.toString()
        holder.descripcionTV.text = permisosModal.descripcion
        if (permisosModal.aprobado) {
            holder.aprobadoTV.text = "Aprobado"
            holder.aprobadoTV.setTextColor( Color.GREEN )
        }else{
            holder.aprobadoTV.text = "Denegado"
            holder.aprobadoTV.setTextColor( Color.RED )
        }

        //Glide.with(context).load(permisosModal.avatar).into(holder.userIV)
    }

    override fun getItemCount(): Int {

        // Returning the size of the array list.
        return permisosModalArrayList.size
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        // Creating variables for our text
        // views and image view.
        val domicilioTV: TextView = itemView.findViewById(R.id.idIVDomicilio)
        val fecha_inicioTV: TextView = itemView.findViewById(R.id.idTVFechaInicio)
        val fecha_finTV: TextView = itemView.findViewById(R.id.idTVFechaFinal)
        val aprobadoTV: TextView = itemView.findViewById(R.id.idTVAprobado)
        val descripcionTV: TextView = itemView.findViewById(R.id.idTVDescripcion)
        val tipoIV: ImageView = itemView.findViewById(R.id.idIVTipo)
    }
}