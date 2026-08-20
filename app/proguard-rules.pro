# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Mantener las librerías de Google API
-keep class com.google.api.** { *; }
-keep interface com.google.api.** { *; }

# Mantener los servicios de Sheets
-keep class com.google.api.services.sheets.** { *; }

# Importante: Mantener HTTP Transport y Jackson/Gson que usa Google
-keep class com.google.api.client.** { *; }
-keep class org.apache.http.** { *; }
-keep class com.fasterxml.jackson.** { *; }

# Mantener tus propios modelos si los usas para mapear los datos de las hojas
-keep class com.larangel.rondy.models.** { *; }

# Evitar que se eliminen atributos necesarios para la reflexión
-keepattributes Signature,RuntimeVisibleAnnotations,AnnotationDefault

-dontwarn com.sun.net.httpserver.Headers
-dontwarn com.sun.net.httpserver.HttpContext
-dontwarn com.sun.net.httpserver.HttpExchange
-dontwarn com.sun.net.httpserver.HttpHandler
-dontwarn com.sun.net.httpserver.HttpServer
-dontwarn java.awt.Desktop$Action
-dontwarn java.awt.Desktop
-dontwarn javax.naming.InvalidNameException
-dontwarn javax.naming.NamingException
-dontwarn javax.naming.directory.Attribute
-dontwarn javax.naming.directory.Attributes
-dontwarn javax.naming.ldap.LdapName
-dontwarn javax.naming.ldap.Rdn
-dontwarn org.ietf.jgss.GSSContext
-dontwarn org.ietf.jgss.GSSCredential
-dontwarn org.ietf.jgss.GSSException
-dontwarn org.ietf.jgss.GSSManager
-dontwarn org.ietf.jgss.GSSName
-dontwarn org.ietf.jgss.Oid