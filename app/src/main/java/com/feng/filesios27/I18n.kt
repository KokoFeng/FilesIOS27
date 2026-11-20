package com.feng.filesios27

import android.content.res.Resources
import android.view.View
import java.text.SimpleDateFormat
import java.util.Locale

// ====================================================================================
// Multi-bahasa: English, Русский, العربية, 한국어, Bahasa Melayu, Português, Español, Bahasa Indonesia.
// Bahasa otomatis mengikuti bahasa bawaan HP. Bahasa yang belum tersedia memakai English.
// Semua teks sumber ditulis dalam English; kamus di bawah menerjemahkannya saat ditampilkan.
// Placeholder: {n} = angka, {x} = ekstensi huruf besar, {0} = teks bebas.
// ====================================================================================
object I18n {
    val NAMES = listOf(
        "en" to "English", "ru" to "Русский", "ar" to "العربية", "ko" to "한국어",
        "ms" to "Bahasa Melayu", "pt" to "Português", "es" to "Español", "id" to "Bahasa Indonesia"
    )
    private val SUPPORTED = NAMES.map { it.first }
    private val IDX = mapOf("ru" to 0, "ar" to 1, "ko" to 2, "ms" to 3, "pt" to 4, "es" to 5, "id" to 6)
    private val AT = mapOf("ru" to "в", "ar" to "في", "ko" to "", "ms" to "pada", "pt" to "às", "es" to "a las", "id" to "pukul")

    /** Bahasa yang sedang dipakai aplikasi. */
    @Volatile var lang: String = "en"
    /** Bahasa bawaan HP (diambil dari sistem, bukan dari Locale.getDefault yang bisa kita ubah). */
    var sysLocale: Locale = Locale.getDefault()

    class E(val key: String, val tr: Array<String>)
    private fun e(key: String, ru: String, ar: String, ko: String, ms: String, pt: String, es: String, id: String) =
        E(key, arrayOf(ru, ar, ko, ms, pt, es, id))

    private class Pat(val re: Regex, val toks: List<String>, val tr: Array<String>)

    private val exact = HashMap<String, Array<String>>()
    private val pats = ArrayList<Pat>()
    private val tokRe = Regex("\\{(n|x|z|[0-9])\\}")

    private val ENTRIES: List<E> = listOf(
        e("Automatic", "Автоматически", "تلقائي", "자동", "Automatik", "Automático", "Automático", "Otomatis"),
        e("Light", "Светлая", "فاتح", "라이트", "Cerah", "Claro", "Claro", "Terang"),
        e("Dark", "Тёмная", "داكن", "다크", "Gelap", "Escuro", "Oscuro", "Gelap"),
        e("Browse", "Обзор", "تصفح", "둘러보기", "Semak Imbas", "Explorar", "Explorar", "Jelajah"),
        e("On My iPhone", "На iPhone", "على iPhone", "나의 iPhone", "Pada iPhone Saya", "No iPhone", "En mi iPhone", "Di iPhone Saya"),
        e("Search", "Поиск", "بحث", "검색", "Cari", "Buscar", "Buscar", "Cari"),
        e("Favorites", "Избранное", "المفضلة", "즐겨찾기", "Kegemaran", "Favoritos", "Favoritos", "Favorit"),
        e("SD Card", "SD-карта", "بطاقة SD", "SD 카드", "Kad SD", "Cartão SD", "Tarjeta SD", "Kartu SD"),
        e("Recently Deleted", "Недавно удалённые", "المحذوفة مؤخرًا", "최근 삭제된 항목", "Baru Dipadam", "Excluídos Recentemente", "Eliminados recientemente", "Baru Dihapus"),
        e("Locations", "Места", "المواقع", "위치", "Lokasi", "Locais", "Ubicaciones", "Lokasi"),
        e("Tags", "Теги", "الوسوم", "태그", "Teg", "Tags", "Etiquetas", "Tag"),
        e("Edit Favorites", "Изменить избранное", "تعديل المفضلة", "즐겨찾기 편집", "Edit Kegemaran", "Editar Favoritos", "Editar favoritos", "Edit Favorit"),
        e("Appearance", "Оформление", "المظهر", "화면 스타일", "Penampilan", "Aparência", "Apariencia", "Tampilan"),
        e("Settings", "Настройки", "الإعدادات", "설정", "Tetapan", "Ajustes", "Ajustes", "Pengaturan"),
        e("OK", "OK", "حسنًا", "확인", "OK", "OK", "OK", "OK"),
        e("Done", "Готово", "تم", "완료", "Selesai", "Concluído", "Listo", "Selesai"),
        e("Choose which folders appear under Favorites on the Browse screen.", "Выберите папки, которые отображаются в разделе «Избранное» на экране «Обзор».", "اختر المجلدات التي تظهر ضمن المفضلة في شاشة التصفح.", "탐색 화면의 즐겨찾기에 표시할 폴더를 선택하세요.", "Pilih folder yang dipaparkan di bawah Kegemaran pada skrin Semak Imbas.", "Escolha quais pastas aparecem em Favoritos na tela Explorar.", "Elige qué carpetas aparecen en Favoritos en la pantalla Explorar.", "Pilih folder yang muncul di Favorit pada layar Jelajah."),
        e("Remove", "Убрать", "إزالة", "제거", "Alih Keluar", "Remover", "Quitar", "Hapus"),
        e("Add Folder…", "Добавить папку…", "إضافة مجلد…", "폴더 추가…", "Tambah Folder…", "Adicionar Pasta…", "Añadir carpeta…", "Tambah Folder…"),
        e("Add", "Добавить", "إضافة", "추가", "Tambah", "Adicionar", "Añadir", "Tambah"),
        e("Choose Folder", "Выбрать папку", "اختيار مجلد", "폴더 선택", "Pilih Folder", "Escolher Pasta", "Elegir carpeta", "Pilih Folder"),
        e("Up one level", "На уровень вверх", "مستوى للأعلى", "상위 폴더로", "Naik satu tahap", "Subir um nível", "Subir un nivel", "Naik satu tingkat"),
        e("Reduce Transparency turns off the glass blur and uses solid surfaces.", "«Понизить прозрачность» отключает размытие стекла и использует сплошные поверхности.", "يؤدي تقليل الشفافية إلى إيقاف تمويه الزجاج واستخدام أسطح صلبة.", "투명도 줄이기는 유리 블러를 끄고 단색 표면을 사용합니다.", "Kurangkan Ketelusan mematikan kabur kaca dan menggunakan permukaan pepejal.", "Reduzir Transparência desativa o desfoque do vidro e usa superfícies sólidas.", "Reducir transparencia desactiva el desenfoque del cristal y usa superficies sólidas.", "Kurangi Transparansi mematikan blur kaca dan menggunakan permukaan solid."),
        e("Tinted", "С оттенком", "ملوّن", "틴트", "Berwarna", "Colorido", "Teñido", "Berwarna"),
        e("Clear", "Прозрачный", "شفاف", "투명", "Jernih", "Transparente", "Transparente", "Bening"),
        e("Choose how clear the glass looks.", "Выберите прозрачность стекла.", "اختر مدى شفافية الزجاج.", "유리의 투명한 정도를 선택하세요.", "Pilih tahap kejernihan kaca.", "Escolha o quão transparente o vidro parece.", "Elige qué tan transparente se ve el cristal.", "Pilih seberapa bening tampilan kaca."),
        e("Text Size", "Размер текста", "حجم النص", "텍스트 크기", "Saiz Teks", "Tamanho do Texto", "Tamaño del texto", "Ukuran Teks"),
        e("Default", "По умолчанию", "افتراضي", "기본", "Lalai", "Padrão", "Predeterminado", "Standar"),
        e("Medium", "Средний", "متوسط", "중간", "Sederhana", "Médio", "Mediano", "Sedang"),
        e("Large", "Большой", "كبير", "크게", "Besar", "Grande", "Grande", "Besar"),
        e("Reduce Transparency", "Понизить прозрачность", "تقليل الشفافية", "투명도 줄이기", "Kurangkan Ketelusan", "Reduzir Transparência", "Reducir transparencia", "Kurangi Transparansi"),
        e("Browsing", "Просмотр", "التصفح", "탐색", "Semakan Imbas", "Navegação", "Exploración", "Penjelajahan"),
        e("Default View", "Вид по умолчанию", "العرض الافتراضي", "기본 보기", "Paparan Lalai", "Visualização Padrão", "Vista predeterminada", "Tampilan Standar"),
        e("Icons", "Значки", "أيقونات", "아이콘", "Ikon", "Ícones", "Iconos", "Ikon"),
        e("List", "Список", "قائمة", "목록", "Senarai", "Lista", "Lista", "Daftar"),
        e("Sort By", "Сортировка", "ترتيب حسب", "정렬 기준", "Isih Mengikut", "Ordenar Por", "Ordenar por", "Urutkan Berdasarkan"),
        e("Name", "Имя", "الاسم", "이름", "Nama", "Nome", "Nombre", "Nama"),
        e("Kind", "Тип", "النوع", "종류", "Jenis", "Tipo", "Tipo", "Jenis"),
        e("Date", "Дата", "التاريخ", "날짜", "Tarikh", "Data", "Fecha", "Tanggal"),
        e("Size", "Размер", "الحجم", "크기", "Saiz", "Tamanho", "Tamaño", "Ukuran"),
        e("Haptic Feedback", "Тактильный отклик", "الاستجابة اللمسية", "햅틱 피드백", "Maklum Balas Haptik", "Resposta Tátil", "Respuesta háptica", "Umpan Balik Haptik"),
        e("General", "Основные", "عام", "일반", "Am", "Geral", "General", "Umum"),
        e("Language", "Язык", "اللغة", "언어", "Bahasa", "Idioma", "Idioma", "Bahasa"),
        e("Run Setup Again", "Запустить настройку снова", "تشغيل الإعداد مرة أخرى", "설정 다시 실행", "Jalankan Persediaan Semula", "Executar Configuração Novamente", "Ejecutar configuración de nuevo", "Jalankan Pengaturan Lagi"),
        e("Restore Defaults", "Восстановить значения по умолчанию", "استعادة الإعدادات الافتراضية", "기본값 복원", "Pulihkan Lalai", "Restaurar Padrões", "Restaurar valores predeterminados", "Pulihkan Default"),
        e("Restore Defaults?", "Восстановить значения по умолчанию?", "استعادة الإعدادات الافتراضية؟", "기본값을 복원할까요?", "Pulihkan Lalai?", "Restaurar Padrões?", "¿Restaurar valores predeterminados?", "Pulihkan Default?"),
        e("Appearance, glass, favorites and view options will return to their original values.", "Оформление, стекло, избранное и параметры вида будут возвращены к исходным значениям.", "ستعود إعدادات المظهر والزجاج والمفضلة وخيارات العرض إلى قيمها الأصلية.", "화면 스타일, 유리, 즐겨찾기 및 보기 옵션이 원래 값으로 돌아갑니다.", "Penampilan, kaca, kegemaran dan pilihan paparan akan dikembalikan kepada nilai asal.", "Aparência, vidro, favoritos e opções de visualização voltarão aos valores originais.", "La apariencia, el cristal, los favoritos y las opciones de vista volverán a sus valores originales.", "Tampilan, kaca, favorit, dan opsi tampilan akan kembali ke nilai awal."),
        e("Cancel", "Отменить", "إلغاء", "취소", "Batal", "Cancelar", "Cancelar", "Batal"),
        e("Restore", "Восстановить", "استعادة", "복원", "Pulihkan", "Restaurar", "Restaurar", "Pulihkan"),
        e("About", "О приложении", "حول", "정보", "Perihal", "Sobre", "Acerca de", "Tentang"),
        e("Default icons and the app icon are locked and cannot be changed.", "Стандартные значки и значок приложения заблокированы и не могут быть изменены.", "الأيقونات الافتراضية وأيقونة التطبيق مقفلة ولا يمكن تغييرها.", "기본 아이콘과 앱 아이콘은 잠겨 있어 변경할 수 없습니다.", "Ikon lalai dan ikon apl dikunci dan tidak boleh diubah.", "Os ícones padrão e o ícone do app estão bloqueados e não podem ser alterados.", "Los iconos predeterminados y el icono de la app están bloqueados y no se pueden cambiar.", "Ikon bawaan dan ikon aplikasi dikunci dan tidak dapat diubah."),
        e("App", "Приложение", "التطبيق", "앱", "Apl", "App", "App", "Aplikasi"),
        e("This app was made by", "Это приложение создано", "تم إنشاء هذا التطبيق بواسطة", "이 앱을 만든 사람", "Apl ini dibuat oleh", "Este app foi criado por", "Esta app fue creada por", "Apps ini dibuat oleh"),
        e("App Version", "Версия приложения", "إصدار التطبيق", "앱 버전", "Versi Apl", "Versão do App", "Versión de la app", "Versi aplikasi"),
        e("This App Has Been Modified", "Это приложение было изменено", "تم تعديل هذا التطبيق", "이 앱이 수정되었습니다", "Apl Ini Telah Diubah Suai", "Este App Foi Modificado", "Esta app ha sido modificada", "Apps Ini Telah Dimodifikasi"),
        e("This app has been modified.\n\nFor modders: this app will rebel and cannot be used.\n\nPlease install the original version from Feng-Dev / Feng iOS on Droid.", "Это приложение было изменено.\n\nДля модификаторов: приложение взбунтуется и не будет работать.\n\nУстановите оригинальную версию от Feng-Dev / Feng iOS on Droid.", "تم تعديل هذا التطبيق.\n\nلمن يقومون بالتعديل: سيتمرد هذا التطبيق ولا يمكن استخدامه.\n\nيرجى تثبيت النسخة الأصلية من Feng-Dev / Feng iOS on Droid.", "이 앱이 수정되었습니다.\n\n모더 여러분께: 이 앱은 반항하며 사용할 수 없습니다.\n\nFeng-Dev / Feng iOS on Droid의 정식 버전을 설치하세요.", "Apl ini telah diubah suai.\n\nUntuk para modder: apl ini akan memberontak dan tidak boleh digunakan.\n\nSila pasang versi asal daripada Feng-Dev / Feng iOS on Droid.", "Este app foi modificado.\n\nPara modders: este app vai se rebelar e não poderá ser usado.\n\nInstale a versão original de Feng-Dev / Feng iOS on Droid.", "Esta app ha sido modificada.\n\nPara los modders: esta app se rebelará y no se podrá usar.\n\nInstala la versión original de Feng-Dev / Feng iOS on Droid.", "Apps ini telah dimodifikasi.\n\nKhusus untuk para modder / pemodifikasi: apps ini akan memberontak dan tidak bisa digunakan.\n\nSilakan pasang versi asli dari Feng-Dev / Feng iOS on Droid."),
        e("Quit", "Выйти", "خروج", "종료", "Keluar", "Sair", "Salir", "Keluar"),
        e("Compress", "Сжать", "ضغط", "압축", "Mampat", "Comprimir", "Comprimir", "Kompres"),
        e("Compressed", "Сжато", "تم الضغط", "압축됨", "Dimampatkan", "Comprimido", "Comprimido", "Dikompres"),
        e("Copy", "Скопировать", "نسخ", "복사", "Salin", "Copiar", "Copiar", "Salin"),
        e("Copied {n} items", "Скопировано объектов: {n}", "تم نسخ {n} عناصر", "{n}개 항목 복사됨", "{n} item disalin", "{n} itens copiados", "{n} elementos copiados", "{n} item disalin"),
        e("Move", "Переместить", "نقل", "이동", "Alih", "Mover", "Mover", "Pindahkan"),
        e("Open a folder, tap ••• then Paste", "Откройте папку, нажмите ••• и выберите «Вставить»", "افتح مجلدًا، ثم اضغط ••• ثم لصق", "폴더를 열고 •••을 누른 다음 붙여넣기를 선택하세요", "Buka folder, ketik ••• kemudian Tampal", "Abra uma pasta, toque em ••• e depois em Colar", "Abre una carpeta, toca ••• y luego Pegar", "Buka folder, ketuk ••• lalu Tempel"),
        e("Delete", "Удалить", "حذف", "삭제", "Padam", "Excluir", "Eliminar", "Hapus"),
        e("Moved to Recently Deleted", "Перемещено в «Недавно удалённые»", "تم النقل إلى المحذوفة مؤخرًا", "최근 삭제된 항목으로 이동됨", "Dialihkan ke Baru Dipadam", "Movido para Excluídos Recentemente", "Movido a Eliminados recientemente", "Dipindahkan ke Baru Dihapus"),
        e("Download from URL", "Загрузить по ссылке", "تنزيل من رابط", "URL에서 다운로드", "Muat Turun dari URL", "Baixar de URL", "Descargar desde URL", "Unduh dari URL"),
        e("Paste the link of the file.", "Вставьте ссылку на файл.", "الصق رابط الملف.", "파일 링크를 붙여넣으세요.", "Tampal pautan fail.", "Cole o link do arquivo.", "Pega el enlace del archivo.", "Tempel tautan file."),
        e("Next", "Далее", "التالي", "다음", "Seterusnya", "Avançar", "Siguiente", "Berikutnya"),
        e("Invalid link", "Недействительная ссылка", "رابط غير صالح", "잘못된 링크", "Pautan tidak sah", "Link inválido", "Enlace no válido", "Tautan tidak valid"),
        e("Checking link…", "Проверка ссылки…", "جارٍ فحص الرابط…", "링크 확인 중…", "Menyemak pautan…", "Verificando link…", "Comprobando enlace…", "Memeriksa tautan…"),
        e("This Is a Web Page", "Это веб-страница", "هذه صفحة ويب", "웹 페이지입니다", "Ini Halaman Web", "Esta é uma Página da Web", "Esta es una página web", "Ini Halaman Web"),
        e("This link opens a web page, not a file. Download it anyway?", "Эта ссылка открывает веб-страницу, а не файл. Всё равно загрузить?", "يفتح هذا الرابط صفحة ويب وليس ملفًا. هل تريد التنزيل على أي حال؟", "이 링크는 파일이 아닌 웹 페이지를 엽니다. 그래도 다운로드할까요?", "Pautan ini membuka halaman web, bukan fail. Muat turun juga?", "Este link abre uma página da web, não um arquivo. Baixar mesmo assim?", "Este enlace abre una página web, no un archivo. ¿Descargar de todos modos?", "Tautan ini membuka halaman web, bukan file. Tetap unduh?"),
        e("Download", "Загрузить", "تنزيل", "다운로드", "Muat Turun", "Baixar", "Descargar", "Unduh"),
        e("Give this file a name before it downloads.", "Дайте файлу имя перед загрузкой.", "امنح هذا الملف اسمًا قبل التنزيل.", "다운로드하기 전에 파일 이름을 지정하세요.", "Berikan nama kepada fail ini sebelum dimuat turun.", "Dê um nome a este arquivo antes de baixar.", "Dale un nombre a este archivo antes de descargarlo.", "Beri nama file ini sebelum diunduh."),
        e("A name is required", "Требуется имя", "الاسم مطلوب", "이름이 필요합니다", "Nama diperlukan", "Um nome é obrigatório", "Se requiere un nombre", "Nama wajib diisi"),
        e("Starting…", "Запуск…", "جارٍ البدء…", "시작하는 중…", "Memulakan…", "Iniciando…", "Iniciando…", "Memulai…"),
        e("Canceled", "Отменено", "تم الإلغاء", "취소됨", "Dibatalkan", "Cancelado", "Cancelado", "Dibatalkan"),
        e("Downloading {n}%", "Загрузка {n}%", "جارٍ التنزيل {n}%", "다운로드 중 {n}%", "Memuat turun {n}%", "Baixando {n}%", "Descargando {n}%", "Mengunduh {n}%"),
        e("Downloading {z}", "Загрузка {z}", "جارٍ التنزيل {z}", "다운로드 중 {z}", "Memuat turun {z}", "Baixando {z}", "Descargando {z}", "Mengunduh {z}"),
        e("tap to cancel", "нажмите для отмены", "اضغط للإلغاء", "눌러서 취소", "ketik untuk batal", "toque para cancelar", "toca para cancelar", "ketuk untuk membatalkan"),
        e("Download canceled", "Загрузка отменена", "تم إلغاء التنزيل", "다운로드가 취소됨", "Muat turun dibatalkan", "Download cancelado", "Descarga cancelada", "Unduhan dibatalkan"),
        e("The address can't be found, or there is no internet connection.", "Адрес не найден или нет подключения к интернету.", "تعذّر العثور على العنوان أو لا يوجد اتصال بالإنترنت.", "주소를 찾을 수 없거나 인터넷에 연결되어 있지 않습니다.", "Alamat tidak dijumpai, atau tiada sambungan internet.", "Não foi possível encontrar o endereço ou não há conexão com a internet.", "No se encuentra la dirección o no hay conexión a internet.", "Alamat tidak ditemukan, atau tidak ada koneksi internet."),
        e("A secure connection could not be made.", "Не удалось установить безопасное соединение.", "تعذّر إنشاء اتصال آمن.", "보안 연결을 만들 수 없습니다.", "Sambungan selamat tidak dapat dibuat.", "Não foi possível estabelecer uma conexão segura.", "No se pudo establecer una conexión segura.", "Koneksi aman tidak dapat dibuat."),
        e("The server took too long to respond.", "Сервер слишком долго не отвечает.", "استغرق الخادم وقتًا طويلاً للرد.", "서버 응답 시간이 너무 오래 걸립니다.", "Pelayan mengambil masa terlalu lama untuk bertindak balas.", "O servidor demorou muito para responder.", "El servidor tardó demasiado en responder.", "Server terlalu lama merespons."),
        e("Could not connect to the server.", "Не удалось подключиться к серверу.", "تعذّر الاتصال بالخادم.", "서버에 연결할 수 없습니다.", "Tidak dapat menyambung ke pelayan.", "Não foi possível conectar ao servidor.", "No se pudo conectar con el servidor.", "Tidak dapat terhubung ke server."),
        e("Something went wrong.", "Что-то пошло не так.", "حدث خطأ ما.", "문제가 발생했습니다.", "Sesuatu telah berlaku.", "Algo deu errado.", "Algo salió mal.", "Terjadi kesalahan."),
        e("Unable To Download", "Не удалось загрузить", "تعذّر التنزيل", "다운로드할 수 없음", "Tidak Dapat Memuat Turun", "Não Foi Possível Baixar", "No se puede descargar", "Tidak Dapat Mengunduh"),
        e("Downloaded", "Загружено", "تم التنزيل", "다운로드됨", "Dimuat Turun", "Baixado", "Descargado", "Diunduh"),
        e("Folder", "Папка", "مجلد", "폴더", "Folder", "Pasta", "Carpeta", "Folder"),
        e("Image", "Изображение", "صورة", "이미지", "Imej", "Imagem", "Imagen", "Gambar"),
        e("Video", "Видео", "فيديو", "비디오", "Video", "Vídeo", "Vídeo", "Video"),
        e("Audio", "Аудио", "صوت", "오디오", "Audio", "Áudio", "Audio", "Audio"),
        e("Text", "Текст", "نص", "텍스트", "Teks", "Texto", "Texto", "Teks"),
        e("ZIP archive", "ZIP-архив", "أرشيف ZIP", "ZIP 압축 파일", "Arkib ZIP", "Arquivo ZIP", "Archivo ZIP", "Arsip ZIP"),
        e("Android app", "Приложение Android", "تطبيق Android", "Android 앱", "Apl Android", "App Android", "App de Android", "Aplikasi Android"),
        e("Document", "Документ", "مستند", "문서", "Dokumen", "Documento", "Documento", "Dokumen"),
        e("{x} document", "Документ {x}", "مستند {x}", "{x} 문서", "Dokumen {x}", "Documento {x}", "Documento {x}", "Dokumen {x}"),
        e("{n} bytes", "{n} байт", "{n} بايت", "{n}바이트", "{n} bait", "{n} bytes", "{n} bytes", "{n} byte"),
        e("Cannot copy a folder into itself", "Нельзя скопировать папку в саму себя", "لا يمكن نسخ مجلد إلى نفسه", "폴더를 자기 자신에게 복사할 수 없습니다", "Tidak boleh menyalin folder ke dalam dirinya sendiri", "Não é possível copiar uma pasta para dentro dela mesma", "No se puede copiar una carpeta dentro de sí misma", "Tidak dapat menyalin folder ke dalam dirinya sendiri"),
        e("Cannot move a folder into itself", "Нельзя переместить папку в саму себя", "لا يمكن نقل مجلد إلى نفسه", "폴더를 자기 자신으로 이동할 수 없습니다", "Tidak boleh mengalihkan folder ke dalam dirinya sendiri", "Não é possível mover uma pasta para dentro dela mesma", "No se puede mover una carpeta dentro de sí misma", "Tidak dapat memindahkan folder ke dalam dirinya sendiri"),
        e("Bad zip entry", "Некорректная запись ZIP", "عنصر ZIP غير صالح", "잘못된 ZIP 항목", "Entri ZIP tidak sah", "Entrada ZIP inválida", "Entrada ZIP no válida", "Entri ZIP tidak valid"),
        e("Allow Access to Files", "Разрешить доступ к файлам", "السماح بالوصول إلى الملفات", "파일 접근 허용", "Benarkan Akses kepada Fail", "Permitir Acesso aos Arquivos", "Permitir acceso a los archivos", "Izinkan Akses ke File"),
        e("Files needs access to your storage to browse, open and manage your files.", "Файлам нужен доступ к хранилищу, чтобы просматривать, открывать файлы и управлять ими.", "يحتاج تطبيق الملفات إلى الوصول إلى مساحة التخزين لتصفح ملفاتك وفتحها وإدارتها.", "파일 앱이 파일을 탐색, 열기 및 관리하려면 저장 공간에 접근해야 합니다.", "Fail memerlukan akses kepada storan anda untuk menyemak imbas, membuka dan mengurus fail anda.", "O app Arquivos precisa de acesso ao armazenamento para navegar, abrir e gerenciar seus arquivos.", "Archivos necesita acceso al almacenamiento para explorar, abrir y administrar tus archivos.", "File memerlukan akses ke penyimpanan untuk menjelajah, membuka, dan mengelola file Anda."),
        e("Allow Access", "Разрешить доступ", "السماح بالوصول", "접근 허용", "Benarkan Akses", "Permitir Acesso", "Permitir acceso", "Izinkan Akses"),
        e("Allow Installing Apps", "Разрешить установку приложений", "السماح بتثبيت التطبيقات", "앱 설치 허용", "Benarkan Pemasangan Apl", "Permitir Instalar Apps", "Permitir instalar apps", "Izinkan Memasang Aplikasi"),
        e("Files needs permission to install apps from your files.", "Файлам нужно разрешение на установку приложений из ваших файлов.", "يحتاج تطبيق الملفات إلى إذن لتثبيت التطبيقات من ملفاتك.", "파일 앱이 파일에서 앱을 설치하려면 권한이 필요합니다.", "Fail memerlukan kebenaran untuk memasang apl daripada fail anda.", "O app Arquivos precisa de permissão para instalar apps a partir dos seus arquivos.", "Archivos necesita permiso para instalar apps desde tus archivos.", "File memerlukan izin untuk memasang aplikasi dari file Anda."),
        e("Not Now", "Не сейчас", "ليس الآن", "나중에", "Bukan Sekarang", "Agora Não", "Ahora no", "Nanti Saja"),
        e("Recents", "Недавние", "الأخيرة", "최근 항목", "Terkini", "Recentes", "Recientes", "Terbaru"),
        e("Shared", "Общие", "المشتركة", "공유됨", "Dikongsi", "Compartilhados", "Compartido", "Dibagikan"),
        e("Yesterday", "Вчера", "أمس", "어제", "Semalam", "Ontem", "Ayer", "Kemarin"),
        e("1 item", "1 объект", "عنصر واحد", "1개 항목", "1 item", "1 item", "1 elemento", "1 item"),
        e("{n} items", "Объектов: {n}", "{n} عنصر", "{n}개 항목", "{n} item", "{n} itens", "{n} elementos", "{n} item"),
        e("No Items", "Нет объектов", "لا توجد عناصر", "항목 없음", "Tiada Item", "Nenhum Item", "Sin elementos", "Tidak Ada Item"),
        e("Voice search unavailable", "Голосовой поиск недоступен", "البحث الصوتي غير متاح", "음성 검색을 사용할 수 없음", "Carian suara tidak tersedia", "Pesquisa por voz indisponível", "Búsqueda por voz no disponible", "Pencarian suara tidak tersedia"),
        e("Select Items", "Выбрать объекты", "تحديد العناصر", "항목 선택", "Pilih Item", "Selecionar Itens", "Seleccionar elementos", "Pilih Item"),
        e("{n} Selected", "Выбрано: {n}", "تم تحديد {n}", "{n}개 선택됨", "{n} Dipilih", "{n} Selecionado(s)", "{n} seleccionados", "{n} Dipilih"),
        e("\"{0}\" was deleted recently. Recover it to open it.", "«{0}» был недавно удалён. Восстановите его, чтобы открыть.", "تم حذف \"{0}\" مؤخرًا. استرجعه لفتحه.", "\"{0}\"이(가) 최근에 삭제되었습니다. 열려면 복구하세요.", "\"{0}\" telah dipadam baru-baru ini. Pulihkan untuk membukanya.", "\"{0}\" foi excluído recentemente. Recupere-o para abri-lo.", "\"{0}\" se eliminó recientemente. Recupéralo para abrirlo.", "\"{0}\" baru saja dihapus. Pulihkan untuk membukanya."),
        e("Recover", "Восстановить", "استرجاع", "복구", "Pulihkan", "Recuperar", "Recuperar", "Pulihkan"),
        e("Recovered", "Восстановлено", "تم الاسترجاع", "복구됨", "Dipulihkan", "Recuperado", "Recuperado", "Dipulihkan"),
        e("Select", "Выбрать", "تحديد", "선택", "Pilih", "Selecionar", "Seleccionar", "Pilih"),
        e("Delete All", "Удалить все", "حذف الكل", "모두 삭제", "Padam Semua", "Excluir Tudo", "Eliminar todo", "Hapus Semua"),
        e("New Folder", "Новая папка", "مجلد جديد", "새로운 폴더", "Folder Baharu", "Nova Pasta", "Nueva carpeta", "Folder Baru"),
        e("untitled folder", "папка без названия", "مجلد بلا عنوان", "이름 없는 폴더", "folder tanpa tajuk", "pasta sem título", "carpeta sin título", "folder tanpa judul"),
        e("Folder created", "Папка создана", "تم إنشاء المجلد", "폴더가 생성됨", "Folder dicipta", "Pasta criada", "Carpeta creada", "Folder dibuat"),
        e("Name already taken", "Имя уже занято", "الاسم مستخدم بالفعل", "이미 사용 중인 이름", "Nama sudah digunakan", "Nome já em uso", "Ese nombre ya está en uso", "Nama sudah dipakai"),
        e("Paste", "Вставить", "لصق", "붙여넣기", "Tampal", "Colar", "Pegar", "Tempel"),
        e("as Icons", "Как значки", "كأيقونات", "아이콘으로", "sebagai Ikon", "como Ícones", "como iconos", "sebagai Ikon"),
        e("as List", "Как список", "كقائمة", "목록으로", "sebagai Senarai", "como Lista", "como lista", "sebagai Daftar"),
        e("Failed: {0}", "Ошибка: {0}", "فشل: {0}", "실패: {0}", "Gagal: {0}", "Falha: {0}", "Error: {0}", "Gagal: {0}"),
        e("Copied", "Скопировано", "تم النسخ", "복사됨", "Disalin", "Copiado", "Copiado", "Disalin"),
        e("Duplicate", "Дублировать", "تكرار", "복제", "Pendua", "Duplicar", "Duplicar", "Duplikat"),
        e("Duplicated", "Продублировано", "تم التكرار", "복제됨", "Diduakan", "Duplicado", "Duplicado", "Diduplikasi"),
        e("Info", "Информация", "معلومات", "정보", "Maklumat", "Informações", "Información", "Info"),
        e("Open", "Открыть", "فتح", "열기", "Buka", "Abrir", "Abrir", "Buka"),
        e("Quick Look", "Быстрый просмотр", "نظرة سريعة", "훑어보기", "Pandang Sekilas", "Visualização Rápida", "Vista rápida", "Quick Look"),
        e("Folder Color", "Цвет папки", "لون المجلد", "폴더 색상", "Warna Folder", "Cor da Pasta", "Color de la carpeta", "Warna Folder"),
        e("Rename", "Переименовать", "إعادة تسمية", "이름 변경", "Namakan Semula", "Renomear", "Renombrar", "Ganti Nama"),
        e("Install", "Установить", "تثبيت", "설치", "Pasang", "Instalar", "Instalar", "Pasang"),
        e("Share", "Поделиться", "مشاركة", "공유", "Kongsi", "Compartilhar", "Compartir", "Bagikan"),
        e("Markup", "Разметка", "ترميز", "마크업", "Markah", "Marcação", "Marcación", "Markup"),
        e("Uncompress", "Распаковать", "فك الضغط", "압축 풀기", "Nyahmampat", "Descompactar", "Descomprimir", "Ekstrak"),
        e("Remove Tag", "Удалить тег", "إزالة الوسم", "태그 제거", "Alih Keluar Teg", "Remover Tag", "Quitar etiqueta", "Hapus Tag"),
        e("Cannot rename", "Не удалось переименовать", "تعذّرت إعادة التسمية", "이름을 변경할 수 없음", "Tidak dapat menamakan semula", "Não foi possível renomear", "No se puede renombrar", "Tidak dapat mengganti nama"),
        e("Extracting…", "Извлечение…", "جارٍ الاستخراج…", "압축 푸는 중…", "Mengekstrak…", "Extraindo…", "Extrayendo…", "Mengekstrak…"),
        e("Extracted", "Извлечено", "تم الاستخراج", "압축 풀기 완료", "Diekstrak", "Extraído", "Extraído", "Diekstrak"),
        e("OPEN", "ОТКРЫТЬ", "فتح", "열기", "BUKA", "ABRIR", "ABRIR", "BUKA"),
        e("Information", "Информация", "المعلومات", "정보", "Maklumat", "Informações", "Información", "Informasi"),
        e("Show More", "Показать больше", "عرض المزيد", "더 보기", "Tunjukkan Lagi", "Mostrar Mais", "Mostrar más", "Tampilkan Lainnya"),
        e("Show Less", "Показать меньше", "عرض أقل", "간단히 보기", "Tunjukkan Kurang", "Mostrar Menos", "Mostrar menos", "Tampilkan Lebih Sedikit"),
        e("Created", "Создан", "تاريخ الإنشاء", "생성일", "Dicipta", "Criado", "Creado", "Dibuat"),
        e("Modified", "Изменён", "تاريخ التعديل", "수정일", "Diubah suai", "Modificado", "Modificado", "Diubah"),
        e("Last opened", "Последнее открытие", "آخر فتح", "마지막으로 연 날짜", "Terakhir dibuka", "Última abertura", "Última apertura", "Terakhir dibuka"),
        e("Where", "Где", "الموقع", "위치", "Lokasi", "Local", "Ubicación", "Lokasi"),
        e("Add Tags", "Добавить теги", "إضافة وسوم", "태그 추가", "Tambah Teg", "Adicionar Tags", "Añadir etiquetas", "Tambah Tag"),
        e("Undo", "Отменить", "تراجع", "실행 취소", "Buat asal", "Desfazer", "Deshacer", "Urungkan"),
        e("Markup saved as a copy", "Разметка сохранена как копия", "تم حفظ الترميز كنسخة", "마크업이 사본으로 저장됨", "Markah disimpan sebagai salinan", "Marcação salva como cópia", "Marcación guardada como copia", "Markup disimpan sebagai salinan"),
        e("Cannot save markup", "Не удалось сохранить разметку", "تعذّر حفظ الترميز", "마크업을 저장할 수 없음", "Tidak dapat menyimpan markah", "Não foi possível salvar a marcação", "No se puede guardar la marcación", "Tidak dapat menyimpan markup"),
        e("Moved", "Перемещено", "تم النقل", "이동됨", "Dialihkan", "Movido", "Movido", "Dipindahkan"),
        e("No mail app found", "Почтовое приложение не найдено", "لم يتم العثور على تطبيق بريد", "메일 앱을 찾을 수 없음", "Apl mel tidak dijumpai", "Nenhum app de e-mail encontrado", "No se encontró ninguna app de correo", "Aplikasi email tidak ditemukan"),
        e("Saved", "Сохранено", "تم الحفظ", "저장됨", "Disimpan", "Salvo", "Guardado", "Tersimpan"),
        e("Saved to Downloads", "Сохранено в «Загрузки»", "تم الحفظ في التنزيلات", "다운로드에 저장됨", "Disimpan ke Muat Turun", "Salvo em Downloads", "Guardado en Descargas", "Disimpan ke Unduhan"),
        e("Compress the folder first", "Сначала сожмите папку", "اضغط المجلد أولاً", "먼저 폴더를 압축하세요", "Mampatkan folder dahulu", "Comprima a pasta primeiro", "Comprime la carpeta primero", "Kompres folder terlebih dahulu"),
        e("Messages", "Сообщения", "الرسائل", "메시지", "Mesej", "Mensagens", "Mensajes", "Pesan"),
        e("Mail", "Почта", "البريد", "Mail", "Mel", "Mail", "Mail", "Mail"),
        e("Save Video", "Сохранить видео", "حفظ الفيديو", "비디오 저장", "Simpan Video", "Salvar Vídeo", "Guardar vídeo", "Simpan Video"),
        e("Save Image", "Сохранить изображение", "حفظ الصورة", "이미지 저장", "Simpan Imej", "Salvar Imagem", "Guardar imagen", "Simpan Gambar"),
        e("Save to Files", "Сохранить в «Файлы»", "حفظ في الملفات", "파일에 저장", "Simpan ke Fail", "Salvar em Arquivos", "Guardar en Archivos", "Simpan ke File"),
        e("Open in…", "Открыть в…", "فتح في…", "다음에서 열기…", "Buka dalam…", "Abrir em…", "Abrir en…", "Buka di…"),
        e("Open Settings manually", "Откройте настройки вручную", "افتح الإعدادات يدويًا", "설정을 직접 여십시오", "Buka Tetapan secara manual", "Abra os Ajustes manualmente", "Abre Ajustes manualmente", "Buka Pengaturan secara manual"),
        e("Unable To Install", "Не удалось установить", "تعذّر التثبيت", "설치할 수 없음", "Tidak Dapat Memasang", "Não Foi Possível Instalar", "No se puede instalar", "Tidak Dapat Menginstal"),
        e("Files isn't allowed to install apps yet. Tap Settings and turn on \"Allow from this source\".", "Файлам пока нельзя устанавливать приложения. Нажмите «Настройки» и включите «Разрешить из этого источника».", "لا يُسمح لتطبيق الملفات بتثبيت التطبيقات بعد. اضغط على الإعدادات وفعّل \"السماح من هذا المصدر\".", "파일 앱은 아직 앱을 설치할 수 없습니다. 설정을 누르고 \"이 출처 허용\"을 켜세요.", "Fail belum dibenarkan memasang apl. Ketik Tetapan dan hidupkan \"Benarkan daripada sumber ini\".", "O app Arquivos ainda não tem permissão para instalar apps. Toque em Ajustes e ative \"Permitir desta fonte\".", "Archivos aún no tiene permiso para instalar apps. Toca Ajustes y activa \"Permitir desde esta fuente\".", "File belum diizinkan memasang aplikasi. Ketuk Pengaturan dan aktifkan \"Izinkan dari sumber ini\"."),
        e("This file can't be installed.", "Этот файл нельзя установить.", "لا يمكن تثبيت هذا الملف.", "이 파일은 설치할 수 없습니다.", "Fail ini tidak boleh dipasang.", "Este arquivo não pode ser instalado.", "Este archivo no se puede instalar.", "File ini tidak dapat diinstal."),
        e("No app on this phone can install this kind of file.", "Ни одно приложение на этом телефоне не может установить такой файл.", "لا يوجد تطبيق على هذا الهاتف يمكنه تثبيت هذا النوع من الملفات.", "이 휴대폰의 어떤 앱도 이런 종류의 파일을 설치할 수 없습니다.", "Tiada apl pada telefon ini yang boleh memasang jenis fail ini.", "Nenhum app neste telefone pode instalar este tipo de arquivo.", "Ninguna app de este teléfono puede instalar este tipo de archivo.", "Tidak ada aplikasi di ponsel ini yang dapat memasang jenis file ini."),
        e("No app can open this file", "Нет приложения для открытия этого файла", "لا يوجد تطبيق يمكنه فتح هذا الملف", "이 파일을 열 수 있는 앱이 없음", "Tiada apl boleh membuka fail ini", "Nenhum app pode abrir este arquivo", "Ninguna app puede abrir este archivo", "Tidak ada aplikasi yang dapat membuka file ini"),
        e("Change the name or keep it, then tap Done.", "Измените имя или оставьте его, затем нажмите «Готово».", "غيّر الاسم أو اتركه، ثم اضغط على تم.", "이름을 변경하거나 그대로 두고 완료를 누르세요.", "Tukar nama atau kekalkan, kemudian ketik Selesai.", "Altere o nome ou mantenha-o e toque em Concluído.", "Cambia el nombre o déjalo y toca Listo.", "Ubah nama atau biarkan, lalu ketuk Selesai."),
        e("Choose Action", "Выберите действие", "اختر إجراءً", "동작 선택", "Pilih Tindakan", "Escolher Ação", "Elegir acción", "Pilih Tindakan"),
        e("Choose desired action to perform with this.", "Выберите действие для этого файла.", "اختر الإجراء المطلوب تنفيذه.", "수행할 동작을 선택하세요.", "Pilih tindakan yang dikehendaki.", "Escolha a ação desejada.", "Elige la acción que quieres realizar.", "Pilih tindakan yang diinginkan."),
        e("Extract", "Извлечь", "استخراج", "압축 풀기", "Ekstrak", "Extrair", "Extraer", "Ekstrak"),
        e("Share File", "Поделиться файлом", "مشاركة الملف", "파일 공유", "Kongsi Fail", "Compartilhar Arquivo", "Compartir archivo", "Bagikan File"),
        e("Custom Install", "Особая установка", "تثبيت مخصص", "사용자 설치", "Pemasangan Tersuai", "Instalação Personalizada", "Instalación personalizada", "Instal Kustom"),
        e("Share App", "Поделиться приложением", "مشاركة التطبيق", "앱 공유", "Kongsi Apl", "Compartilhar App", "Compartir app", "Bagikan Aplikasi"),
        e("Unsigned install", "Установка без подписи", "تثبيت بدون توقيع", "서명 없이 설치", "Pemasangan tanpa tandatangan", "Instalar sem assinatura", "Instalar sin firma", "Instal tanpa tanda tangan"),
        e("“Files” would like to install “{0}”", "«Файлы» хотят установить «{0}»", "يريد تطبيق “الملفات” تثبيت “{0}”", "“파일”에서 “{0}” 앱을 설치하려고 합니다", "“Fail” ingin memasang “{0}”", "“Arquivos” quer instalar “{0}”", "“Archivos” quiere instalar “{0}”", "“File” ingin memasang “{0}”"),
        e("Not a valid app package. No signature found.", "Недопустимый пакет приложения. Подпись не найдена.", "حزمة تطبيق غير صالحة. لم يتم العثور على توقيع.", "올바른 앱 패키지가 아닙니다. 서명을 찾을 수 없습니다.", "Bukan pakej apl yang sah. Tiada tandatangan dijumpai.", "Pacote de app inválido. Nenhuma assinatura encontrada.", "Paquete de app no válido. No se encontró ninguna firma.", "Bukan paket aplikasi yang valid. Tanda tangan tidak ditemukan."),
        e("Version", "Версия", "الإصدار", "버전", "Versi", "Versão", "Versión", "Versi"),
        e("Signed", "Подписано", "موقّع", "서명됨", "Ditandatangani", "Assinado", "Firmado", "Bertanda tangan"),
        e("Cannot read signature.", "Не удалось прочитать подпись.", "تعذّرت قراءة التوقيع.", "서명을 읽을 수 없습니다.", "Tidak dapat membaca tandatangan.", "Não foi possível ler a assinatura.", "No se puede leer la firma.", "Tidak dapat membaca tanda tangan."),
        e("Signature", "Подпись", "التوقيع", "서명", "Tandatangan", "Assinatura", "Firma", "Tanda Tangan"),
        e("This font can't be previewed.", "Этот шрифт нельзя просмотреть.", "لا يمكن معاينة هذا الخط.", "이 서체는 미리 볼 수 없습니다.", "Fon ini tidak boleh dipratonton.", "Esta fonte não pode ser visualizada.", "Esta fuente no se puede previsualizar.", "Font ini tidak dapat dipratinjau."),
        e("This archive can't be listed (only ZIP-based files).", "Содержимое этого архива нельзя показать (только ZIP-файлы).", "لا يمكن عرض محتويات هذا الأرشيف (ملفات ZIP فقط).", "이 압축 파일의 목록을 볼 수 없습니다 (ZIP 기반 파일만 가능).", "Arkib ini tidak boleh disenaraikan (fail berasaskan ZIP sahaja).", "Não é possível listar este arquivo (apenas arquivos baseados em ZIP).", "No se puede listar este archivo (solo archivos basados en ZIP).", "Arsip ini tidak dapat ditampilkan (hanya file berbasis ZIP)."),
        e("{n} items · {0} · tap a file to peek", "Объектов: {n} · {0} · коснитесь файла для просмотра", "{n} عنصر · {0} · اضغط على ملف لمعاينته", "{n}개 항목 · {0} · 파일을 눌러 미리보기", "{n} item · {0} · ketik fail untuk mengintai", "{n} itens · {0} · toque em um arquivo para espiar", "{n} elementos · {0} · toca un archivo para ver", "{n} item · {0} · ketuk file untuk mengintip"),
        e("Missing entry", "Запись отсутствует", "العنصر مفقود", "항목을 찾을 수 없음", "Entri tiada", "Entrada ausente", "Falta la entrada", "Entri tidak ada"),
        e("Cannot open this item", "Не удалось открыть этот объект", "تعذّر فتح هذا العنصر", "이 항목을 열 수 없음", "Tidak dapat membuka item ini", "Não foi possível abrir este item", "No se puede abrir este elemento", "Tidak dapat membuka item ini"),
        e("Cannot open this PDF", "Не удалось открыть этот PDF", "تعذّر فتح ملف PDF هذا", "이 PDF를 열 수 없음", "Tidak dapat membuka PDF ini", "Não foi possível abrir este PDF", "No se puede abrir este PDF", "Tidak dapat membuka PDF ini"),
        e("Empty file", "Пустой файл", "ملف فارغ", "빈 파일", "Fail kosong", "Arquivo vazio", "Archivo vacío", "File kosong"),
        e("Showing the first {n} bytes.", "Показаны первые {n} байт.", "عرض أول {n} بايت.", "처음 {n}바이트를 표시합니다.", "Menunjukkan {n} bait pertama.", "Mostrando os primeiros {n} bytes.", "Mostrando los primeros {n} bytes.", "Menampilkan {n} byte pertama."),
        e("Delete Now", "Удалить сейчас", "حذف الآن", "지금 삭제", "Padam Sekarang", "Excluir Agora", "Eliminar ahora", "Hapus Sekarang"),
        e("Delete \"{0}\"?", "Удалить «{0}»?", "حذف \"{0}\"؟", "\"{0}\" 항목을 삭제할까요?", "Padam \"{0}\"?", "Excluir \"{0}\"?", "¿Eliminar \"{0}\"?", "Hapus \"{0}\"?"),
        e("Delete {n} items?", "Удалить объекты ({n})?", "حذف {n} عناصر؟", "{n}개 항목을 삭제할까요?", "Padam {n} item?", "Excluir {n} itens?", "¿Eliminar {n} elementos?", "Hapus {n} item?"),
        e("This can't be undone.", "Это действие нельзя отменить.", "لا يمكن التراجع عن هذا.", "이 동작은 취소할 수 없습니다.", "Ini tidak boleh dibuat asal.", "Isto não pode ser desfeito.", "Esto no se puede deshacer.", "Tindakan ini tidak dapat dibatalkan."),
        e("Deleted", "Удалено", "تم الحذف", "삭제됨", "Dipadam", "Excluído", "Eliminado", "Dihapus"),
        e("Bad redirect", "Некорректное перенаправление", "إعادة توجيه غير صالحة", "잘못된 리디렉션", "Ubah hala tidak sah", "Redirecionamento inválido", "Redirección no válida", "Pengalihan tidak valid"),
        e("Too many redirects", "Слишком много перенаправлений", "عدد كبير جدًا من عمليات إعادة التوجيه", "리디렉션이 너무 많음", "Terlalu banyak ubah hala", "Redirecionamentos demais", "Demasiadas redirecciones", "Terlalu banyak pengalihan"),
        e("Nothing to download", "Нечего загружать", "لا يوجد شيء للتنزيل", "다운로드할 항목 없음", "Tiada untuk dimuat turun", "Nada para baixar", "Nada que descargar", "Tidak ada yang diunduh"),
        e("Give this file a name.", "Дайте файлу имя.", "امنح هذا الملف اسمًا.", "파일 이름을 지정하세요.", "Berikan nama kepada fail ini.", "Dê um nome a este arquivo.", "Dale un nombre a este archivo.", "Beri nama file ini."),
        e("Saving…", "Сохранение…", "جارٍ الحفظ…", "저장 중…", "Menyimpan…", "Salvando…", "Guardando…", "Menyimpan…"),
        e("Cannot read file", "Не удалось прочитать файл", "تعذّرت قراءة الملف", "파일을 읽을 수 없음", "Tidak dapat membaca fail", "Não foi possível ler o arquivo", "No se puede leer el archivo", "Tidak dapat membaca file"),
        e("Cast isn't available on this phone", "Трансляция недоступна на этом телефоне", "الإرسال غير متاح على هذا الهاتف", "이 휴대폰에서는 캐스트를 사용할 수 없음", "Cast tidak tersedia pada telefon ini", "Transmitir não está disponível neste telefone", "Transmitir no está disponible en este teléfono", "Cast tidak tersedia di ponsel ini"),
        e("Playback Speed", "Скорость воспроизведения", "سرعة التشغيل", "재생 속도", "Kelajuan Main Balik", "Velocidade de Reprodução", "Velocidad de reproducción", "Kecepatan Pemutaran"),
        e("Normal", "Обычная", "عادية", "보통", "Biasa", "Normal", "Normal", "Normal"),
        e("Cannot play this file", "Не удалось воспроизвести этот файл", "تعذّر تشغيل هذا الملف", "이 파일을 재생할 수 없음", "Tidak dapat memainkan fail ini", "Não foi possível reproduzir este arquivo", "No se puede reproducir este archivo", "Tidak dapat memutar file ini"),
        e("File is too large to open", "Файл слишком большой для открытия", "الملف كبير جدًا لفتحه", "파일이 너무 커서 열 수 없음", "Fail terlalu besar untuk dibuka", "O arquivo é grande demais para abrir", "El archivo es demasiado grande para abrirlo", "File terlalu besar untuk dibuka"),
        e("Save", "Сохранить", "حفظ", "저장", "Simpan", "Salvar", "Guardar", "Simpan"),
        e("Cannot save", "Не удалось сохранить", "تعذّر الحفظ", "저장할 수 없음", "Tidak dapat menyimpan", "Não foi possível salvar", "No se puede guardar", "Tidak dapat menyimpan"),
        e("Back", "Назад", "رجوع", "뒤로", "Kembali", "Voltar", "Atrás", "Kembali"),
        e("Choose how you would like text and icons to look on this phone.", "Выберите, как текст и значки будут выглядеть на этом телефоне.", "اختر شكل النص والأيقونات على هذا الهاتف.", "이 휴대폰에서 텍스트와 아이콘이 보이는 방식을 선택하세요.", "Pilih rupa teks dan ikon pada telefon ini.", "Escolha como o texto e os ícones aparecem neste telefone.", "Elige cómo se verán el texto y los iconos en este teléfono.", "Pilih tampilan teks dan ikon di ponsel ini."),
        e("Continue", "Продолжить", "متابعة", "계속", "Teruskan", "Continuar", "Continuar", "Lanjutkan"),
        e("Quick Start", "Быстрое начало", "البدء السريع", "빠른 시작", "Mula Pantas", "Início Rápido", "Inicio rápido", "Mulai Cepat"),
        e("Looking for your files…", "Поиск ваших файлов…", "جارٍ البحث عن ملفاتك…", "파일을 찾는 중…", "Mencari fail anda…", "Procurando seus arquivos…", "Buscando tus archivos…", "Mencari file Anda…"),
        e("Files needs access to this phone's storage to show your documents, downloads and photos.", "Файлам нужен доступ к хранилищу телефона, чтобы показывать ваши документы, загрузки и фото.", "يحتاج تطبيق الملفات إلى الوصول إلى مساحة تخزين هذا الهاتف لعرض مستنداتك وتنزيلاتك وصورك.", "파일 앱이 문서, 다운로드 및 사진을 표시하려면 이 휴대폰의 저장 공간에 접근해야 합니다.", "Fail memerlukan akses kepada storan telefon ini untuk menunjukkan dokumen, muat turun dan foto anda.", "O app Arquivos precisa de acesso ao armazenamento deste telefone para mostrar seus documentos, downloads e fotos.", "Archivos necesita acceso al almacenamiento de este teléfono para mostrar tus documentos, descargas y fotos.", "Files perlu akses ke penyimpanan ponsel ini untuk menampilkan dokumen, unduhan, dan foto Anda."),
        e("After you tap Allow Access, turn on \"Allow access to manage all files\" for Files, then come back.", "После нажатия «Разрешить доступ» включите «Разрешить доступ ко всем файлам» для Файлов и вернитесь сюда.", "بعد الضغط على السماح بالوصول، فعّل \"السماح بإدارة جميع الملفات\" لتطبيق الملفات، ثم عد إلى هنا.", "접근 허용을 누른 후 파일 앱에서 \"모든 파일 관리 허용\"을 켜고 돌아오세요.", "Selepas anda mengetik Benarkan Akses, hidupkan \"Benarkan akses untuk mengurus semua fail\" untuk Fail, kemudian kembali ke sini.", "Depois de tocar em Permitir Acesso, ative \"Permitir acesso para gerenciar todos os arquivos\" para o Arquivos e volte aqui.", "Después de tocar Permitir acceso, activa \"Permitir acceso para administrar todos los archivos\" para Archivos y vuelve aquí.", "Setelah menekan Izinkan Akses, aktifkan \"Izinkan akses untuk mengelola semua file\" untuk Files, lalu kembali ke sini."),
        e("Set Up Later", "Настроить позже", "الإعداد لاحقًا", "나중에 설정", "Sediakan Kemudian", "Configurar Mais Tarde", "Configurar más tarde", "Atur Nanti"),
        e("Red", "Красный", "أحمر", "빨간색", "Merah", "Vermelho", "Rojo", "Merah"),
        e("Orange", "Оранжевый", "برتقالي", "주황색", "Oren", "Laranja", "Naranja", "Oranye"),
        e("Yellow", "Жёлтый", "أصفر", "노란색", "Kuning", "Amarelo", "Amarillo", "Kuning"),
        e("Green", "Зелёный", "أخضر", "초록색", "Hijau", "Verde", "Verde", "Hijau"),
        e("Blue", "Синий", "أزرق", "파란색", "Biru", "Azul", "Azul", "Biru"),
        e("Purple", "Фиолетовый", "بنفسجي", "보라색", "Ungu", "Roxo", "Morado", "Ungu"),
        e("Gray", "Серый", "رمادي", "회색", "Kelabu", "Cinza", "Gris", "Abu-abu"),
        e("Pink", "Розовый", "وردي", "분홍색", "Merah Jambu", "Rosa", "Rosa", "Merah Muda")
    )

    init {
        val list = ArrayList<Pair<String, Pat>>()
        for (en in ENTRIES) {
            if (en.key.contains("{")) {
                val toks = ArrayList<String>()
                val sb = StringBuilder("^")
                var last = 0
                for (m in tokRe.findAll(en.key)) {
                    sb.append(Regex.escape(en.key.substring(last, m.range.first)))
                    val t = m.groupValues[1]
                    toks.add(t)
                    sb.append(when (t) { "n" -> "(\\d[\\d.,]*)"; "x" -> "([A-Z0-9]{1,10})"; "z" -> "(\\d[\\d.,]* ?[A-Za-z]+)"; else -> "(.+?)" })
                    last = m.range.last + 1
                }
                sb.append(Regex.escape(en.key.substring(last))).append("$")
                list.add(en.key to Pat(Regex(sb.toString(), RegexOption.DOT_MATCHES_ALL), toks, en.tr))
            } else exact[en.key] = en.tr
        }
        // pola yang lebih panjang (lebih spesifik) dicoba lebih dulu
        list.sortByDescending { it.first.length }
        for (p in list) pats.add(p.second)
    }

    /** Ubah kode bahasa tersimpan (atau null = otomatis) menjadi bahasa yang dipakai. */
    fun resolve(saved: String?): String {
        val c = when (saved) {
            null -> sysLocale.language
            "in" -> "id"
            else -> saved
        }
        val cc = if (c == "in") "id" else c
        return if (cc in SUPPORTED) cc else "en"
    }

    fun nameOf(code: String): String = NAMES.firstOrNull { it.first == code }?.second ?: "English"

    private val SEPS = listOf("\n", " \u2013 ", " \u00B7 ", " \u2022 ", " - ")

    /** Terjemahkan teks English ke bahasa aktif. Jika tidak ada di kamus, dikembalikan apa adanya. */
    fun t(s: String): String {
        if (lang == "en" || s.isEmpty()) return s
        val i = IDX[lang] ?: return s
        return tr(s, i)
    }

    private fun tr(s: String, i: Int): String {
        exact[s]?.let { return it[i] }
        if (s[0].isLowerCase()) {
            val cap = s.replaceFirstChar { it.uppercase() }
            exact[cap]?.let { r -> return r[i].replaceFirstChar { it.lowercase() } }
        }
        for (p in pats) {
            val m = p.re.matchEntire(s) ?: continue
            var out = p.tr[i]
            for ((gi, t) in p.toks.withIndex()) out = out.replace("{" + t + "}", m.groupValues[gi + 1])
            return out
        }
        for (sep in SEPS) {
            if (s.contains(sep)) {
                val parts = s.split(sep)
                val res = parts.map { if (it.isEmpty()) it else tr(it, i) }
                if (res != parts) return res.joinToString(sep)
            }
        }
        return s
    }

    /** Ganti kata 'at' pada pola tanggal dengan kata bahasa aktif. */
    fun dt(p: String): String {
        val w = AT[lang] ?: return p
        return if (w.isEmpty()) p.replace(" 'at'", "") else p.replace("'at'", "'" + w + "'")
    }

    fun systemLocaleNow(): Locale = try {
        Resources.getSystem().configuration.locales.get(0)
    } catch (e: Exception) { Locale.getDefault() }
}

/** Arah tata letak: Arab dari kanan ke kiri, bahasa lain kiri ke kanan. */
fun MainActivity.applyDirection() {
    root.layoutDirection = if (I18n.lang == "ar") View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
}

/** Ganti bahasa aplikasi. code == null berarti otomatis mengikuti bahasa HP. */
fun MainActivity.setAppLanguage(code: String?) {
    if (code == null) prefs.edit().remove("lang").apply() else prefs.edit().putString("lang", code).apply()
    applyLocalePrefs()
    applyDirection()
    applyTheme(true)
}

fun MainActivity.langRow(label: String, selected: Boolean, onClick: () -> Unit): View {
    val r = settingsRowBase()
    r.background = pressBg()
    r.setOnClickListener { onClick() }
    r.addView(tv(label, 17f, C.TEXT), llp(0, WRAP, 1f))
    if (selected) r.addView(icon(P.CHECK, C.BLUE, 20), llp(dpi(20), dpi(20)))
    return r
}

fun MainActivity.languagePage(): View {
    val page = android.widget.LinearLayout(this).apply {
        orientation = android.widget.LinearLayout.VERTICAL
        setBackgroundColor(C.BG)
    }
    val head = android.widget.FrameLayout(this).apply { setPadding(dpi(12), topInset + dpi(8), dpi(12), dpi(6)) }
    head.addView(circle(P.BACK, false) { popPage() }, flp(dpi(40), dpi(40), android.view.Gravity.START))
    head.addView(tv("Language", 17f, C.TEXT, true).apply { gravity = android.view.Gravity.CENTER }, flp(WRAP, dpi(40), android.view.Gravity.CENTER_HORIZONTAL))
    page.addView(head)
    val sv = android.widget.ScrollView(this).apply {
        isVerticalScrollBarEnabled = false
        clipToPadding = false
        setPadding(0, 0, 0, botInset + dpi(110))
    }
    val col = android.widget.LinearLayout(this).apply {
        orientation = android.widget.LinearLayout.VERTICAL
        setPadding(dpi(16), 0, dpi(16), 0)
    }
    sv.addView(col)
    page.addView(sv, llp(MATCH, 0, 1f))
    val saved = prefs.getString("lang", null)
    val rows = ArrayList<View>()
    rows.add(langRow("Automatic", saved == null) { setAppLanguage(null) })
    for ((code, name) in I18n.NAMES) rows.add(langRow(name, saved == code) { setAppLanguage(code) })
    settingsGroup(col, null, null, rows)
    return page
}
