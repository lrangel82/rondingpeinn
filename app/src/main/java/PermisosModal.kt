import android.content.Context
import android.graphics.Color
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.larangel.rondingpeinn.R
import java.time.LocalDate
import java.time.LocalDateTime


data class PermisosModal (
    var fechaCreado: LocalDateTime,
    var calle: String,
    var numero: String,
    var solicitante: String,
    var correo: String,
    var tipoAcceso: String,
    var tipo: String,
    var fechaInicio: LocalDate,
    var fechaFin: LocalDate,
    var descripcion: String,
    var nombrePersonas: String,
    var aprobado: Boolean,
    var motivo_denegado: String,
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
        val direccion: String = permisosModal.calle.substring(0,3) + permisosModal.numero
        val desc: String = "[${permisosModal.tipoAcceso}] -->" + permisosModal.descripcion + "\n Personas:\n" + permisosModal.nombrePersonas +"\n\n"+permisosModal.motivo_denegado
        holder.domicilioTV.text = direccion
        holder.fecha_inicioTV.text = permisosModal.fechaInicio.toString()
        holder.fecha_finTV.text = permisosModal.fechaFin.toString()
        holder.descripcionTV.text = desc
        if (permisosModal.aprobado) {
            holder.aprobadoTV.text = "Aprobado"
            holder.aprobadoTV.setTextColor( Color.GREEN )
            holder.domicilioTV.setBackgroundColor(Color.GREEN)
        }else{
            holder.aprobadoTV.text = "Denegado"
            holder.aprobadoTV.setTextColor( Color.RED )
            holder.domicilioTV.setBackgroundColor(Color.RED)
        }
        when (permisosModal.tipo){
            "Trabajador(es)" -> holder.tipoIV.setImageResource(R.drawable.permiso_trabajadores)
            "Mudanza" -> holder.tipoIV.setImageResource(R.drawable.permiso_mudanza)
            "Muebles" -> holder.tipoIV.setImageResource(R.drawable.permiso_muebles)
            "Construccion" -> holder.tipoIV.setImageResource(R.drawable.permiso_construccion)
            "Servicios" ->  holder.tipoIV.setImageResource(R.drawable.permiso_servicios)
            else -> holder.tipoIV.setImageResource(R.drawable.permiso_otro)
        }
        holder.itemPermiso.setOnClickListener {
            if(holder.descripcionTV.textSize >= 40  ){
                holder.descripcionTV.setTextSize(TypedValue.COMPLEX_UNIT_SP,15f)
            }else{
                holder.descripcionTV.setTextSize(TypedValue.COMPLEX_UNIT_SP,30f)
            }
        }

    }

    private fun pxToSp(px: Float, context: Context): Float {
        return px / context.resources.displayMetrics.scaledDensity
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
        val itemPermiso: RelativeLayout = itemView.findViewById(R.id.itemPermiso)
    }
}