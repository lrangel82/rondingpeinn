// Extensions.kt
package com.larangel.rondy.utils

/**
 * Busca una placa en un texto usando Regex y devuelve el valor o null
 */
fun String.extraerPlaca(): String? {
    val plateRegex = Regex("([A-Z]{3}[0-9]{3,4}[A-Z]?|[0-9]{2}[A-Z][0-9]{3}|[0-9]{3}[A-Z]{3}|[A-Z]{2}[0-9]{4,5}[A-Z]?|[A-Z][0-9]{4}|[A-Z][0-9]{2}[A-Z]{2,3}|[A-Z]{3}[0-9][A-Z]|[A-Z]{5}[0-9]{2})")
    return plateRegex.find(this.uppercase())?.value
}

/**
 * Busca un TAG valido en el texto
 */
fun String.extraerTAG(): String? {
    val TagRegex = Regex("([0-9]{8}|[0-9]{7})")
    return TagRegex.find(this.uppercase())?.value
}

/**
 * Busca un COLOR valido en el texto
 */
fun String.extraerColor(): String? {
    val ColorRegex = Regex("(rojo|verde|azul|magenta|lila|morado|rosa|turquesa|amarillo|blanco|negro|cafe|marron|violeta|naranja|beige|gris|plata)")
    return ColorRegex.find(this.lowercase())?.value
}

/**
 * Busca un MARCA de un auto valido en el texto
 */
fun String.extraerMarcaAuto(): String? {
    val MarcaRegex = Regex("(YAMAHA|Acura|Alfa Romeo|Audi|Auteco|Bentley|BMW|Changan|Chirey|Chrysler|Fiat|Ford Motor|Foton|General Motors|Great Wall Motor|Honda|Hyundai|Infiniti|Isuzu|JAC|Jaguar|JETOUR|KIA|Land Rover|Lexus|Lincoln|Mazda|Mercedes Benz|MG Motor|MG ROVER|Mini|Mitsubishi|MOTORNATION|Nissan|Omoda|Peugeot|Porsche|Renault|SEAT|Smart|Subaru|Suzuki|Toyota|Volkswagen|Volvo)",
        RegexOption.IGNORE_CASE)
    return MarcaRegex.find(this)?.value
}