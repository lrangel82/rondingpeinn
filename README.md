# RondinGPEinn
Aplicacion para android para realizar el rondin de los guardias

Permite leer NFC tags con valores de ubicacion y una etiqueta, los cuales pueden ser enviados
posteriormente a whatsup con una imagen de google maps que marca los puntos donde se leyeron los TAGs
asi como el orden en que se hizo, con una lista indicando fecha y hora de cada evento.

# Crear los spreadsheets
https://www.geeksforgeeks.org/how-to-read-data-from-google-spreadsheet-in-android/

    example https://docs.google.com/spreadsheets/d/e/2PACX-1vQDS4yz6MLos5Se56p0CUIJrA1jO6nLr7JC_eZiTT3s8xg8hEI2gSTeTrYSqVHyrTVvw6Z15KsmhSUO/pub?output=csv
Creating a URL for fetching our data from Google Spreadsheet:
 * Domiclio
 * Tipo
 * Fecha inicio
 * Fecha fin
 * Descripcion
 * Aprobado
 * Procesado

Then publish to web and select publish as cCSV, copy the url and save it, 
you can configure this URL later in the config section of the app.