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
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.larangel.rondingpeinn.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers


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
        val context = holder.itemView.context

        // Setting data to our text views.
        val direccion: String = permisosModal.calle.substring(0,3) + permisosModal.numero
        val desc: String = "[${permisosModal.tipoAcceso}] -->" + permisosModal.descripcion + "\n Personas:\n" + permisosModal.nombrePersonas +"\n\n"+permisosModal.motivo_denegado
        holder.domicilioTV.text = direccion
        holder.fecha_inicioTV.text = permisosModal.fechaInicio.toString()
        holder.fecha_finTV.text = permisosModal.fechaFin.toString()
        holder.descripcionTV.text = desc
        if (permisosModal.procesado == false) {
            holder.aprobadoTV.text = ">>NUEVO<< Por VALIDAR ADMIN"
            holder.aprobadoTV.setTextColor( Color.YELLOW )
            holder.domicilioTV.setBackgroundColor(Color.YELLOW)
        } else if (permisosModal.aprobado) {
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
            if (!permisosModal.procesado) {
                mostrarDialogoDecision(context, permisosModal, position)
            }
        }

    }

//    private fun pxToSp(px: Float, context: Context): Float {
//        return px / context.resources.displayMetrics.scaledDensity
//    }
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

    private fun mostrarDialogoDecision(context: Context, permiso: PermisosModal, position: Int) {
        val builder = androidx.appcompat.app.AlertDialog.Builder(context)
        builder.setTitle("Gestión de Permiso")
        builder.setMessage("¿Qué desea hacer para este permiso?")

        builder.setPositiveButton("Aprobar") { _, _ ->
            permiso.procesado = true
            permiso.aprobado = true
            //UPDATE APROBADO
            if (updatePermiso(context,permiso)){
                notifyItemChanged(position)
                enviarCorreoAprobado(context, permiso)
            }
            else
                Toast.makeText(
                    context,
                    "Error al Actualizar Permiso...",
                    Toast.LENGTH_LONG
                ).show()
        }

        builder.setNegativeButton("Denegar") { _, _ ->
            mostrarDialogoRazonDenegacion(context, permiso, position)
        }

        builder.setNeutralButton("Cancelar") { dialog, _ ->
            dialog.dismiss()
        }

        builder.show()
    }

    private fun mostrarDialogoRazonDenegacion(context: Context, permiso: PermisosModal, position: Int) {
        val input = android.widget.EditText(context)
        input.hint = "Escriba la razón aquí..."

        androidx.appcompat.app.AlertDialog.Builder(context)
            .setTitle("Razón de Denegación")
            .setView(input)
            .setPositiveButton("Denegar") { _, _ ->
                val razon = input.text.toString()
                permiso.motivo_denegado = razon
                permiso.procesado = true
                permiso.aprobado = false
                //UPDATE APROBADO
                if (updatePermiso(context,permiso)) {
                    notifyItemChanged(position)
                    enviarCorreoDenegado(context, permiso)
                }
                else
                    Toast.makeText(
                        context,
                        "Error al Actualizar Permiso...",
                        Toast.LENGTH_LONG
                    ).show()
            }
            .setNegativeButton("Atrás", null)
            .show()
    }

    private fun updatePermiso(context: Context, permiso: PermisosModal): Boolean{
        val dataRaw = DataRawRondin(context,CoroutineScope(Dispatchers.IO))
        val newData = listOf(
            permiso.fechaCreado.toString(),             // FechaInicio
            permiso.calle.toString(),                   // Calle
            permiso.numero.toString(),                  // Numero
            permiso.solicitante.toString(),             // Solicitante
            permiso.correo.toString(),                  // email
            permiso.tipoAcceso.toString(),              // Ingreso/Egreso
            permiso.tipo.toString(),                    // Tipo Permiso
            permiso.fechaInicio.toString(),             // Fecha Ini
            permiso.fechaFin.toString(),                // Fecha Fin
            permiso.descripcion.toString(),             // Descripcion
            permiso.nombrePersonas.toString(),          // Nombre personas acceso
            if(permiso.aprobado) "1" else "0",          // Aporbado
            permiso.motivo_denegado.toString(),         // Motivo Denegado
            if(permiso.procesado) "Si" else "No"        // Procesado por ROBOT
        )
        return dataRaw.updatePermisoCache(newData)
    }

    private fun enviarCorreoAprobado(context: Context, permiso: PermisosModal) {
        //UPDATE APROBADO
        updatePermiso(context,permiso)

        val sujeto = "Permiso APROBADO: ${permiso.tipo} - ${permiso.calle} ${permiso.numero}"
        val cuerpo = """
        <!DOCTYPE html>
        <html lang="es">
        <head>
            <meta charset="UTF-8">
            <title>Su permiso ha sido APROBADO</title>
        </head>
        <body>
            <img src="https://lh7-us.googleusercontent.com/Dnh2ahdVtjFhMfAYbX900qZAgFwqDpnPsIrpVPYJWbB0P0Bo3XBvUFH0grcQg-HLcCfxrbOVfwrcnZt9b8qRHhop1ZCEURK1-71MT--1Qv_hIhAvDXKMi-d1212AEYQpJ8prPJhb4iqfUEfTCarcSL3vIfpyRGQPP_akpczJkuz7cTXN5p34eRJvgjHX0If52WmDhi3O?key=JeFt4ibeyu8-7qsUmC1ZjQ">
            <p>Estimado/a ${permiso.solicitante},<br><br>
        
            Nos complace informarle que su solicitud de permiso ha sido <h3><font color="green">APROBADA.</font></h3><br>
            </p>
            <p>Detalles:<br/>
                - Tipo: <b>${permiso.tipo}</b><br/>
                - Ubicación: <b>${permiso.calle} ${permiso.numero}</b><br>
                - Vigencia: del <b>${permiso.fechaInicio} al ${permiso.fechaFin}</b><br>
                - Descripción: <b>${permiso.descripcion}</b><br>
            </p>
        
            <p>
            Saludos cordiales,<br>
            Administración.
            </p>
        </body>
        </html>
    """.trimIndent()

        dispararIntentCorreo(context, permiso.correo, sujeto, cuerpo)
    }

    private fun enviarCorreoDenegado(context: Context, permiso: PermisosModal) {
        val sujeto = "Permiso DENEGADO: ${permiso.tipo} - ${permiso.calle} ${permiso.numero}"
        val cuerpo = """
        <!DOCTYPE html>
        <html lang="es">
        <head>
            <meta charset="UTF-8">
            <title>Su permiso ha sido DENEGADO</title>
        </head>
        <body>
            <img src="https://lh7-us.googleusercontent.com/Dnh2ahdVtjFhMfAYbX900qZAgFwqDpnPsIrpVPYJWbB0P0Bo3XBvUFH0grcQg-HLcCfxrbOVfwrcnZt9b8qRHhop1ZCEURK1-71MT--1Qv_hIhAvDXKMi-d1212AEYQpJ8prPJhb4iqfUEfTCarcSL3vIfpyRGQPP_akpczJkuz7cTXN5p34eRJvgjHX0If52WmDhi3O?key=JeFt4ibeyu8-7qsUmC1ZjQ">
            <p>Estimado/a ${permiso.solicitante},<br><br>
        
            Lamentamos informarle que su solicitud de permiso ha sido <h3><font color="red">DENEGADA.</font></h3><br>
            </p>
            <p>
            Razón de la denegación:</br>
            ${permiso.motivo_denegado}
            </p>
        
            <p>Si tiene dudas, favor de contactar a la administración.</p>
        </body>
        </html>
    """.trimIndent()

        dispararIntentCorreo(context, permiso.correo, sujeto, cuerpo)
    }

    private fun dispararIntentCorreo(context: Context, emailDestino: String, asunto: String, mensaje: String) {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:") // Solo apps de email
            putExtra(Intent.EXTRA_EMAIL, arrayOf(emailDestino))
            putExtra(Intent.EXTRA_SUBJECT, asunto)
            putExtra(Intent.EXTRA_TEXT, mensaje)
        }

        try {
            context.startActivity(Intent.createChooser(intent, "Enviar correo con..."))
        } catch (e: Exception) {
            Toast.makeText(context, "No hay aplicaciones de correo instaladas", Toast.LENGTH_SHORT).show()
        }
    }

}