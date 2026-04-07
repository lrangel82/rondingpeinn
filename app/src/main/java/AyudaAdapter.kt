import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.larangel.rondy.R
import java.time.LocalDate
import java.time.LocalDateTime

data class AyudaSlideItem (
    var imagen_slide: Int,
    var texto_ayuda: String
)

class AyudaAdapter(private val slides_ayuda: List<AyudaSlideItem>) : RecyclerView.Adapter<AyudaAdapter.ViewHolder>() {
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.imgSlide)
        val txtDesc: TextView = view.findViewById(R.id.txtDescripcionSlide)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_ayuda_slide, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.imageView.setImageResource(slides_ayuda[position].imagen_slide) // Aquí pones tus .webp
        holder.txtDesc.setText(slides_ayuda[position].texto_ayuda)
    }

    override fun getItemCount() = slides_ayuda.size
}