import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.larangel.rondy.R

class SplashSlidesDialog(private val images: List<Int>, private val titulo: String) : DialogFragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, android.R.style.Theme_DeviceDefault_Dialog)
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            // 1. Hace que la ventana del diálogo sea transparente para ver la Activity detrás
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))

            // 2. Obliga al diálogo a ocupar toda la pantalla (los márgenes reales se manejarán en el XML)
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

            // 3. Opcional: Ajusta la opacidad del fondo de la Activity detrás (0.0 transparente, 1.0 negro)
            setDimAmount(0.5f)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.layout_splash_slides, container, false)

        val viewPager = view.findViewById<ViewPager2>(R.id.viewPager)
        val btnClose = view.findViewById<ImageButton>(R.id.btnClose)
        val tabLayout = view.findViewById<com.google.android.material.tabs.TabLayout>(R.id.tabLayout)
        val txtTitulo =view.findViewById<TextView>(R.id.txtTitulo)

        txtTitulo.text=titulo
        // Adapter sencillo para las imágenes
        viewPager.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val img = ImageView(parent.context).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                }
                return object : RecyclerView.ViewHolder(img) {}
            }

            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                (holder.itemView as ImageView).setImageResource(images[position])
            }

            override fun getItemCount(): Int = images.size
        }

        com.google.android.material.tabs.TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            // Aquí no ponemos texto porque queremos solo los círculos
        }.attach()

        btnClose.setOnClickListener { dismiss() }

        return view
    }
}
