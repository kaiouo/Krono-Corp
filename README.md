# Krono Corp

Aplicación móvil nativa para Android, hecha con **Kotlin** y **Jetpack Compose**, que ayuda a pequeños y medianos negocios a administrar sus sucursales, su equipo de ventas y su información comercial desde el celular, con un asistente de inteligencia artificial integrado.

El proyecto usa **Appwrite** como backend (autenticación, base de datos y almacenamiento de archivos) y **Groq** como motor de IA para el asistente conversacional.

## 📋 Descripción general

Krono Corp está pensado para negocios con una estructura jerárquica de tres niveles:

- Un **Gerente General**, que administra a los supervisores de todas las sucursales.
- Uno o varios **Dueños de negocio**, que administran su propio negocio y sus sucursales.
- Varios **Supervisores de ventas**, asignados a una sucursal específica, que registran y reportan ventas.

La app permite iniciar sesión, registrar ventas por chat con IA, generar reportes en PDF y Excel, consultar métricas, y administrar cuentas y sucursales según el rol de cada usuario.

## 👥 Roles y funcionalidades

### Gerente General
- Panel de administración con historial de reportes de todos los supervisores.
- Alta de nuevos supervisores mediante un sistema de invitaciones (con notificación de cuentas pendientes).
- Asignación de supervisores a sucursales y gestión de su equipo.
- Configuración del negocio y de las preguntas que usa la IA.
- Métricas consolidadas de todos los supervisores.
- Gestión de sucursales.
- Asistente de IA propio.

### Dueño del negocio
- Gestión de sucursales (crear, editar, eliminar).
- Configuración de cuenta: cambio de contraseña, verificación de correo, notificaciones, cerrar sesión.
- Métricas del negocio: ventas del mes, histórico, promedio por venta, gráficos por producto y por sucursal.
- Asistente de IA para registrar ventas, generar reportes y exportarlos.
- Historial de reportes y de gráficos generados.

### Supervisor de ventas
- Vista de su sucursal asignada.
- Asistente de IA para registrar ventas y generar reportes (PDF/Excel) de su propia sucursal.
- Historial de sus reportes generados.

### Autenticación (todos los roles)
- Inicio de sesión y registro con Appwrite.
- Recuperación de contraseña por correo, con enlace que abre la app directamente (deep link).
- Verificación de correo electrónico, con el mismo mecanismo de enlace directo.

## 🤖 Asistente con IA

El chat (`VentasChat` + `GroqService`) entiende lenguaje natural para:
- Registrar ventas ("vendí 2 camisas a $10 cada una").
- Consultar el historial de ventas.
- Generar reportes y exportarlos a PDF o Excel.
- Generar gráficos de ventas.
- **Agregar columnas/preguntas nuevas a los reportes de Excel**, cuando el usuario lo pide por chat (ej. "agrega una pregunta sobre el método de pago").

Usa el modelo `openai/gpt-oss-120b` a través de la API de Groq.

## 🏗️ Arquitectura del proyecto

```
app/src/main/java/com/example/krono_corp/
├── Login/          Autenticación: login, registro, recuperar contraseña, verificación de correo
├── Admin/          Pantallas del rol Gerente General
├── Supervisor/      Pantallas del rol Supervisor de ventas
├── negocio/        Pantallas del rol Dueño del negocio (sucursales, métricas, configuración, asistente)
├── data/           Repositorios: conexión a Appwrite (cuentas, ventas, sucursales, reportes, invitaciones)
├── ia/             Integración con Groq (chat de IA)
├── reportes/       Generación de PDF y Excel, descarga y notificaciones de reportes
├── components/     Barras de navegación y elementos de UI compartidos
└── ui/             Tema de la aplicación (colores, tipografía)
```

La navegación usa **Jetpack Navigation Compose**, con un grafo de navegación independiente por rol (`AdminScreen`, `SupervisorScreen`, `NegocioNavHost`), después de que el rol del usuario se determina al iniciar sesión.

## 🛠️ Tecnologías

| Categoría | Tecnología |
|---|---|
| Lenguaje | Kotlin |
| UI | Jetpack Compose (Material 3) |
| Backend / Auth / DB | Appwrite (servicio `TablesDB`) |
| Almacenamiento de archivos | Appwrite Storage |
| Inteligencia artificial | Groq Cloud API (`openai/gpt-oss-120b`) |
| Generación de documentos | PDFBox-Android, generación manual de `.xlsx` (OOXML) |
| Navegación | Navigation Compose |
| Build | Gradle (Kotlin DSL), AGP 9.x |

## ⚙️ Configuración y ejecución

1. Clona el repositorio.
2. En la raíz del proyecto, crea (o edita) el archivo `local.properties` con:
   ```
   sdk.dir=<ruta a tu Android SDK>
   GROQ_API_KEY=<tu clave de Groq>
   ```
   Este archivo es local a cada máquina y nunca se sube al repositorio (`.gitignore`).
3. El backend de Appwrite (endpoint, proyecto, base de datos) ya está configurado en `data/AppwriteClient.kt`. Para que el envío de correos de recuperación/verificación funcione, el proyecto de Appwrite debe tener registradas las plataformas `recovery` y `verify` en su consola.
4. Compila y ejecuta:
   ```
   ./gradlew assembleDebug
   ```
   o ábrelo directamente en Android Studio.

## 👥 Equipo

Proyecto desarrollado en equipo para la materia de Desarrollo de Software Móvil (UDB):

- Erick Vladimir Barrientos López - BL230303
- Gustavo Ismael Serrano Rivera - SR251873
- Katherine Estefany Beltrán López - BL233081
- Fernando Antonio Paz López - LP251570

## 📄 Licencia

Este proyecto está bajo la licencia MIT. Ver [LICENSE](LICENSE) para más detalles.

## 📚 Recursos

- [Investigación de Jetpack Compose (PDF)](https://github.com/user-attachments/files/33800642/DSM.Investigacion.Jetpack.Compose.pdf)
