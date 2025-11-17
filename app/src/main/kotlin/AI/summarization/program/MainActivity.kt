package AI.summarization.program

import androidx.drawerlayout.widget.DrawerLayout
import androidx.core.view.GravityCompat
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.ProgressBar
import android.widget.LinearLayout
import com.google.android.material.button.MaterialButton
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import android.os.Handler
import android.os.Looper
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import android.media.MediaRecorder
import java.io.File 
import android.os.Environment
import android.view.GestureDetector
import android.view.MotionEvent
import kotlin.math.abs // Для использования abs() в onFling
import android.view.animation.AnimationUtils // Для смахиваний записи
import android.view.animation.Animation
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.ActivityResultLauncher
import android.app.AlertDialog
import android.content.DialogInterface
import android.view.LayoutInflater

class MainActivity : AppCompatActivity() {

    // UI элементы
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var outputTextView: TextView
    private lateinit var processButton: MaterialButton
    private lateinit var reverseButton: MaterialButton
    private lateinit var recordButton: MaterialButton
    private lateinit var selectFileButton: MaterialButton
    private lateinit var audioInputLayout: LinearLayout
    private lateinit var progressBar: ProgressBar
    //Запись
    private lateinit var recordingControlsLayout: LinearLayout
    private lateinit var deleteRecordingButton: MaterialButton
    private lateinit var pauseResumeButton: MaterialButton
    private lateinit var sendRecordingButton: MaterialButton

    // КЭШ: Переменные для хранения результатов (от LLM и ASR)
    private var originalText: String? = null // Текст, полученный от ASR
    private var summarizedText: String? = null // Результат суммаризации
    private var expandedText: String? = null // Результат "Реверс"

    // Переменные для записи аудио
    private var mediaRecorder: MediaRecorder? = null
    private var audioFilePath: String? = null // Путь к сохраненному файлу
    private var isRecording: Boolean = false
    private val AUDIO_MIME_TYPE = "audio/*"
    
    // Для свайпа вверх (Swipe Gesture)
    private lateinit var gestureDetector: GestureDetector
    private val SWIPE_THRESHOLD = 100 // Мин. расстояние для свайпа в пикселях
    private val SWIPE_VELOCITY_THRESHOLD = 100 // Мин. скорость свайпа

    // Константы для режимов
    private val MODE_INITIAL = 0
    private val MODE_ORIGINAL = 1
    private val MODE_SUMMARIZED = 2
    private val MODE_EXPANDED = 3
    
    private var currentMode: Int = MODE_INITIAL // Текущее состояние вывода

    // Состояния работы
    private var isPaused = false

    // Максимальная продолжительность записи: 10 минут
    private val MAX_RECORDING_DURATION_MS = 600000
    // Для таймера
    private val timerHandler = Handler(Looper.getMainLooper())
    private lateinit var timerRunnable: Runnable
    private var secondsElapsed: Long = 0

    // Временные данные для имитации ИИ-ответа (В реальном приложении будет API-ответ)
    private val MOCK_ORIGINAL = "Отлично, вот мой длинный монолог о тренировке: «Сегодня я впервые попробовал новую схему тренировок, и мои ощущения просто невероятны. Я провёл 5 минут на орбитреке, чтобы разогреться, а потом сразу перешел к плиометрике. Ощущение такое, что мои мышцы работают на пределе, особенно квадрицепсы. Но через 20 минут я почувствовал прилив энергии, и смог увеличить вес на жиме. Мой совет всем — не бойтесь менять рутину! Чувствую, что это был настоящий прорыв, хотя и очень устал. Нужно пересмотреть свой рацион и, возможно, добавить больше белка для восстановления, а то мышцы просто горят. Надеюсь, что эта схема принесет мне желаемый результат уже через месяц."
    private val MOCK_SUMMARIZED = "Спортсмен впервые попробовал новую схему: 5 минут разогрева, затем плиометрика. Сначала мышцы устали, но затем пришел прилив энергии, позволив увеличить вес. Рекомендация: не бояться смены рутины. Нуждается в пересмотре рациона и добавлении белка для восстановления."
    private val MOCK_EXPANDED = "ИИ-анализ и Реверс-вопросы: \n\n1. Питание: Какие конкретно изменения в рационе вы планируете? Какой вид белка вы предпочитаете для быстрого восстановления?\n2. Усталость vs Энергия: Не является ли сильная усталость после 20 минут признаком недостатка гликогена? Стоит ли скорректировать предтренировочный прием пищи?\n3. Методика: Учитывая, что мышцы 'горят', рассмотрите методику контрастных тренировок (контрастный душ) для ускорения выведения лактата."

    private val REQUEST_RECORD_AUDIO_PERMISSION = 200

    //Для загрузки анимаций один раз
    private val slideOutUp by lazy { AnimationUtils.loadAnimation(this, R.anim.slide_out_up) }
    private val slideInDown by lazy { AnimationUtils.loadAnimation(this, R.anim.slide_in_down) }
    private val slideOutDown by lazy { AnimationUtils.loadAnimation(this, R.anim.slide_out_down) }
    private val slideInUp by lazy { AnimationUtils.loadAnimation(this, R.anim.slide_in_up) }
   
    // Запуск Activity для выбора файла и обработка результата
    private lateinit var selectAudioLauncher: ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Инициализация UI элементов
        drawerLayout = findViewById(R.id.drawer_layout)
        outputTextView = findViewById(R.id.output_text_field)
        processButton = findViewById(R.id.process_button)
        reverseButton = findViewById(R.id.reverse_button)
        recordButton = findViewById(R.id.record_button)
        progressBar = findViewById(R.id.progress_bar)
        recordingControlsLayout = findViewById(R.id.recording_controls_layout)
        deleteRecordingButton = findViewById(R.id.delete_recording_button)
        pauseResumeButton = findViewById(R.id.pause_resume_button)
        sendRecordingButton = findViewById(R.id.send_recording_button)
        selectFileButton = findViewById(R.id.select_file_button)

        // Настройка Toolbar для открытия Drawer
        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }
        // Изначальное состояние UI
        outputTextView.text = "Начните запись или выберите файл, чтобы начать обработку."
        processButton.visibility = View.GONE
        reverseButton.visibility = View.GONE

        // Инициализация родительского Layout для жестов
            audioInputLayout = findViewById(R.id.audio_input_layout)
            selectAudioLauncher = registerForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri: Uri? -> 
            uri?.let {
                handleFileSelection(it)
            } ?: run {
                Snackbar.make(drawerLayout, "Выбор аудиофайла отменен.", Snackbar.LENGTH_SHORT).show()
            }
        }
        
        // === 1. Инициализация жестов ===
        gestureDetector = GestureDetector(this, SwipeGestureListener())
       
        // перехват касания кнопки и передача его детектору жестов
        val buttonSwipeFixListener = View.OnTouchListener { view, motionEvent ->
            gestureDetector.onTouchEvent(motionEvent)
            true // Перехват полностью, чтобы сработал жест (swipe или tap)
        }

        // Привязка слушателя к самим кнопкам
        recordButton.setOnTouchListener(buttonSwipeFixListener)
        selectFileButton.setOnTouchListener(buttonSwipeFixListener)
        
        // Привязка слушателя к родительскому Layout (для пустой области)
        audioInputLayout.setOnTouchListener { _, motionEvent -> 
            gestureDetector.onTouchEvent(motionEvent)
            true 
        }                  
        
        // === 2. Обработка нажатия кнопки "Обработать и суммировать" ===
        processButton.setOnClickListener {
            if (currentMode == MODE_ORIGINAL) {
                handleProcessSummarize()
            } else if (currentMode == MODE_EXPANDED) {
                // Если режим - Expanded, кнопка становится "Показать оригинал"
                displayOriginalText()
            }
        }

        // === 3. Обработка нажатия кнопки "Реверс" / "Показать сокращение" ===
        reverseButton.setOnClickListener {
            handleReverseButton()
        }
        // === 4. Обработка кнопок в режиме записи ===
        deleteRecordingButton.setOnClickListener {
            showDeleteConfirmationDialog() // Вызов диалога
        }
        pauseResumeButton.setOnClickListener {
            handlePauseResume()
        }
        sendRecordingButton.setOnClickListener {
            handleStopSend()
        }
        // Проверка, есть ли уже разрешение
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // Если нет, запрашос у пользователя
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                REQUEST_RECORD_AUDIO_PERMISSION
            )
        }
    }

    private fun handleFileSelection(audioUri: Uri) {
        showProgress("Загрузка аудиофайла...") 
        
        Handler(Looper.getMainLooper()).postDelayed({
            audioFilePath = audioUri.toString() 
            
            showTextResult("Файл успешно загружен. Нажмите 'Обработать и суммировать'.")
            
            // Показ кнопок для обработки
            processButton.visibility = View.VISIBLE
            reverseButton.visibility = View.GONE 
            
            // Скрытие нижней панели
            recordingControlsLayout.visibility = View.GONE

        }, 3000) // Имитация обработки 3 секунды
    } 
    
    private fun stopRecording(shouldDeleteFile: Boolean = false) {
        mediaRecorder?.apply {
            try {
                stop() 
                release() 
            } catch (e: RuntimeException) {
                e.printStackTrace()
            }
        }
        mediaRecorder = null
        
        if (shouldDeleteFile && audioFilePath != null) {
            val file = File(audioFilePath!!)
            if (file.exists()) {
                file.delete()
                android.util.Log.i("Audio", "Аудиофайл удален: $audioFilePath")
            }
        }
        audioFilePath = null
    }

    // ===============================================
    //               СМЕНА РЕЖИМА (Микрофон или Файл)
    // ===============================================
    private fun toggleInputMode(directionUp: Boolean) {
        // Определение, используемых анимаций
        val currentOut: Animation
        val currentIn: Animation
        val nextOut: Animation
        val nextIn: Animation

        if (recordButton.visibility == View.VISIBLE) {
            // Режим микрофон смена на файл
            if (directionUp) { // свайп вверх
                currentOut = slideOutUp
                nextIn = slideInDown
                outputTextView.text = "Выберите файл для обработки или смахните вниз для записи."
            } else { // свайп вниз (для отмены свайпа вверх, если кнопка еще не файл)
                currentOut = slideOutDown
                nextIn = slideInUp
                outputTextView.text = "Смахните вверх, чтобы выбрать файл. Начните запись."
            }
            
            // Запуск анимации исчезновения текущей кнопки (Микрофона)
            recordButton.startAnimation(currentOut)

            currentOut.setAnimationListener(object : android.view.animation.Animation.AnimationListener {
                override fun onAnimationEnd(p0: android.view.animation.Animation?) {
                    recordButton.visibility = View.GONE
                    
                    // Анимация появления новой кнопки (Файл)
                    selectFileButton.startAnimation(nextIn)
                    selectFileButton.visibility = View.VISIBLE
                    
                    // Очистка слушателя
                    currentOut.setAnimationListener(null)
                }
                override fun onAnimationStart(p0: android.view.animation.Animation?) {}
                override fun onAnimationRepeat(p0: android.view.animation.Animation?) {}
            })

            if (isRecording) { cancelRecording() }
            
        } else {
            // Режим файл смена на микрофон
            if (directionUp) { // свайп ввей (для отмены свайпа вниз)
                currentOut = slideOutUp
                nextIn = slideInDown
            } else { // свайп вниз
                currentOut = slideOutDown
                nextIn = slideInUp
            }
            
            // Запуск анимации исчезновения текущей кнопки (Файла)
            selectFileButton.startAnimation(currentOut)
            
            currentOut.setAnimationListener(object : android.view.animation.Animation.AnimationListener {
                override fun onAnimationEnd(p0: android.view.animation.Animation?) {
                    selectFileButton.visibility = View.GONE
                    
                    // Анимация появления новой кнопки (Микрофона)
                    recordButton.startAnimation(nextIn)
                    recordButton.visibility = View.VISIBLE
                    
                    // Очистка слушателя
                    currentOut.setAnimationListener(null) 
                }
                override fun onAnimationStart(p0: android.view.animation.Animation?) {}
                override fun onAnimationRepeat(p0: android.view.animation.Animation?) {}
            })
            
            outputTextView.text = "Начните запись или выберите файл, чтобы начать обработку."
        }
    }
    
    // ===============================================
    //               ОБРАБОТКА ЖЕСТОВ (Клики и свайпы)
    // ===============================================
    inner class SwipeGestureListener : GestureDetector.SimpleOnGestureListener() {

        // onTouchListener будет перехватывать клики
        override fun onSingleTapUp(e: MotionEvent): Boolean {
            if (recordButton.visibility == View.VISIBLE) {
                // Проверка сработал ли клик в пределах самой кнопки?
                if (e.x >= 0 && e.x <= recordButton.width && 
                    e.y >= 0 && e.y <= recordButton.height) {
                    
                    startNewRecording() 
                    return true
                }
            }
            else if (selectFileButton.visibility == View.VISIBLE) {
                // Если клик внутри границ selectFileButton
                if (e.x >= 0 && e.x <= selectFileButton.width && 
                    e.y >= 0 && e.y <= selectFileButton.height) {
                    
                    // В режиме файла вызов Launch (аналог setOnClickListener)
                    selectAudioLauncher.launch(AUDIO_MIME_TYPE)
                    return true // Событие обработано
                }
            }
            // Если вернулся сюда, значит, клик был в пустой области.
            return false 
        }
        
        // Обработка свайпа
        override fun onFling(
            e1: MotionEvent?, 
            e2: MotionEvent,  
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            val diffY = e2.y - (e1?.y ?: 0f)
            val diffX = e2.x - (e1?.x ?: 0f)
            
            // Проверка, вертикальный ли свайп и что он значительный
            if (abs(diffY) > abs(diffX) && abs(diffY) > SWIPE_THRESHOLD && abs(velocityY) > SWIPE_VELOCITY_THRESHOLD) {
                if (isRecording) {
                    return false // Игнор свайпа во время активной записи
                }
                if (diffY < 0) { // свайп вверх (diffY отрицательный)
                    toggleInputMode(directionUp = true)
                    return true
                } else if (diffY > 0) { // свайп вниз (diffY положительный)
                    toggleInputMode(directionUp = false)
                    return true
                }
            }
            return false
        }
    }

    // ===============================================
    //               ЛОГИКА СОСТОЯНИЙ
    // ===============================================

    private fun startNewRecording() {
        // 1. Игнор клика, если запись уже идет
        if (isRecording) return

        // 2. Инициализация MediaRecorder
        // Использование конструктора с контекстом для современных API
        mediaRecorder = MediaRecorder(this) 

        // 3. Настройка источника, формата и кодировщика (обязательные шаги)
        mediaRecorder?.setAudioSource(MediaRecorder.AudioSource.MIC)
        mediaRecorder?.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
        mediaRecorder?.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)

        // 4. Установка пути для сохранения (очень важно)
        // Создание временного файла во внешнем хранилище приложения
        val timeStamp = System.currentTimeMillis()
        val outputFile = File(getExternalFilesDir(Environment.DIRECTORY_MUSIC), "temp_audio_${timeStamp}.3gp")
        audioFilePath = outputFile.absolutePath // Сохранение пути
        
        // Устанавка выходного файла
        mediaRecorder?.setOutputFile(audioFilePath) 

        // 5. Установка ограничения (10 минут)
        mediaRecorder?.setMaxDuration(MAX_RECORDING_DURATION_MS)
        
        // 6. Обработка лимита
        mediaRecorder?.setOnInfoListener { _, what, _ -> 
            if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
                Snackbar.make(drawerLayout, "Лимит записи (10 минут) достигнут. Запись автоматически остановлена.", Snackbar.LENGTH_LONG).show()
                handleStopSend()
            }
        }

        // 7. Подготовка и запуск
        try {
            mediaRecorder?.prepare() 
            mediaRecorder?.start()

            isRecording = true
            recordButton.visibility = View.GONE
            selectFileButton.visibility = View.GONE
            recordingControlsLayout.visibility = View.VISIBLE
            startRecordingTimer()

        } catch (e: Exception) {
            // Пойманная ошибка и вывод её сообщения
            Snackbar.make(drawerLayout, "Ошибка при запуске записи: ${e.message}", Snackbar.LENGTH_LONG).show()
            e.printStackTrace()
            isRecording = false // Сброс состояния
        }
    }

    private fun startRecordingTimer() {
        secondsElapsed = 0
        timerRunnable = object : Runnable {
            override fun run() {
                secondsElapsed++
                updateRecordingTime(secondsElapsed, isPaused) 
                timerHandler.postDelayed(this, 1000)
            }
        }
        updateRecordingTime(secondsElapsed, false) 
        timerHandler.post(timerRunnable)
    }

    private fun updateRecordingTime(seconds: Long, isPaused: Boolean) {
        val minutes = seconds / 60
        val secs = seconds % 60
        // Форматирование: 05:30
        val timeFormatted = String.format("%02d:%02d", minutes, secs)
        
        // Определение префикса в зависимости от состояния
        val prefix = if (isPaused) "Запись остановлена: " else "Запись идёт: "
        
        // Обновление текста с новым префиксом
        outputTextView.text = prefix + timeFormatted 
    }

    private fun handleStopSend() {
        if (!isRecording) return

        stopRecording(shouldDeleteFile = false) 

        // Переключение UI, сокрытие панели управления, показ кнопки ввода
        recordingControlsLayout.visibility = View.GONE
        recordButton.visibility = View.VISIBLE
        selectFileButton.visibility = View.GONE

        isRecording = false 

        // Имитация ASR
        showProgress("Запись завершена. Имитация ASR...") 

        // Остановка таймера и сброс счетчика
        timerHandler.removeCallbacks(timerRunnable) 
        secondsElapsed = 0 
        outputTextView.text = "Начните запись или выберите файл, чтобы начать обработку."
        
        // Сброс иконки
        pauseResumeButton.setIconResource(R.drawable.ic_pause_24)

        Handler(Looper.getMainLooper()).postDelayed({
            progressBar.visibility = View.GONE
            originalText = MOCK_ORIGINAL

            // Обновление UI
            displayOriginalText() // Переводит UI в режим MODE_ORIGINAL
            processButton.text = "Обработать и суммировать"
            // processButton.visibility будет установлен в displayOriginalText()
            // reverseButton.visibility будет установлен в displayOriginalText()
        }, 2000) 
    }
    
    private fun handlePauseResume() {
        if (isRecording) {
            if (isPaused) {
                mediaRecorder?.resume()
                isPaused = false
                timerHandler.postDelayed(timerRunnable, 1000)
                updateRecordingTime(secondsElapsed, isPaused) 
                pauseResumeButton.setIconResource(R.drawable.ic_pause_24) 
            } else {
                mediaRecorder?.pause()
                isPaused = true
                timerHandler.removeCallbacks(timerRunnable) 
                updateRecordingTime(secondsElapsed, isPaused) 
                pauseResumeButton.setIconResource(R.drawable.ic_play_24) 
            }
        }
    }

    private fun startRecording() {
        // 1. Создание пути к файлу
        val outputDir = getExternalFilesDir(Environment.DIRECTORY_MUSIC)
        val file = File(outputDir, "recording_${System.currentTimeMillis()}.3gp")
        audioFilePath = file.absolutePath

        // 2. Инициализация MediaRecorder
        mediaRecorder = MediaRecorder(this).apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP) // Стандартный формат
            setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
            setOutputFile(audioFilePath)
            
            try {
                prepare()
                start() // Начало записи
            } catch (e: Exception) {
                e.printStackTrace()
                Snackbar.make(drawerLayout, "Ошибка записи: ${e.message}", Snackbar.LENGTH_LONG).show()
            }
        }
    }
    
    override fun onStop() {
        super.onStop()
        // Обязательно освобождить ресурсы при выходе из прилож
        mediaRecorder?.release()
        mediaRecorder = null
    }
    
    private fun handleProcessSummarize() {
        // Запрос к LLM на суммаризацию
        showProgress("ИИ-обработка и суммаризация...")
        
        // Вызов API в полной версии приложения будет здесь 
        Handler(Looper.getMainLooper()).postDelayed({
            progressBar.visibility = View.GONE

            // Кэщ, сохранение результатов LLM
            summarizedText = MOCK_SUMMARIZED
            expandedText = MOCK_EXPANDED
            
            // Обновление UI
            outputTextView.text = summarizedText
            currentMode = MODE_SUMMARIZED

            // Настройка кнопки
            processButton.visibility = View.GONE // Исчезание кнопки "Обработать" 
            reverseButton.text = "Раскрыть тему (Реверс)" // Появление кнопки "Реверс"
            reverseButton.visibility = View.VISIBLE
            
            Snackbar.make(drawerLayout, "Суммаризация завершена!", Snackbar.LENGTH_SHORT).show()

        }, 3000) // Имитация задержки LLM в 3 секунды
    }

    private fun handleReverseButton() {
        when (currentMode) {
            MODE_SUMMARIZED -> {
                // Переключение на Expanded (Реверс)
                showProgress("ИИ-анализ (Реверс)...")
                
                // Здесь показывается уже кэшированный expandedText в полном приложении 
                Handler(Looper.getMainLooper()).postDelayed({
                    progressBar.visibility = View.GONE
                    outputTextView.text = expandedText
                    currentMode = MODE_EXPANDED
                    
                    processButton.text = "Показать оригинал" // ProcessButton меняет функцию
                    processButton.visibility = View.VISIBLE // ProcessButton появляется
                    reverseButton.text = "Показать сокращение" // ReverseButton меняет надпись
                    
                    Snackbar.make(drawerLayout, "Анализ завершен!", Snackbar.LENGTH_SHORT).show()
                }, 1500) // Имитация задержки Реверс-переключения в 1.5 секунды
            }
            MODE_EXPANDED -> {
                // Переключение на Summarized (Сокращение)
                outputTextView.text = summarizedText
                currentMode = MODE_SUMMARIZED
                
                processButton.visibility = View.GONE
                reverseButton.text = "Раскрыть тему (Реверс)"
                
                Snackbar.make(drawerLayout, "Показан сокращенный текст.", Snackbar.LENGTH_SHORT).show()
            }
        }
    }
       
    private fun displayOriginalText() {
        // Показ оригинального текста, полученного после ASR
        outputTextView.text = originalText
        currentMode = MODE_ORIGINAL
        
        processButton.text = "Обработать и суммировать"
        processButton.visibility = View.VISIBLE
        reverseButton.visibility = View.GONE
    }

    private fun showProgress(message: String) {
        outputTextView.text = message
        progressBar.visibility = View.VISIBLE
        processButton.visibility = View.GONE
        reverseButton.visibility = View.GONE
    }

    private fun showTextResult(text: String) {
        // 1. Установка итогового текста
        outputTextView.text = text
        
        // 2. Сокрытие индикатора загрузки
        progressBar.visibility = View.GONE
        
        // 3. Сокрытие кнопки управления записью
        recordingControlsLayout.visibility = View.GONE

        // 4. Показ кнопки "Обработать" и "Реверс"
        processButton.visibility = View.VISIBLE
        reverseButton.visibility = View.GONE 
    }

    private fun showDeleteConfirmationDialog() {
        // 1. Создание LayoutInflater для "раздувания" макета
        val customView = LayoutInflater.from(this).inflate(R.layout.custom_alert_dialog, null)

        // 2. Поиск элементов внутри пользовательского макета
        val dialogTitle = customView.findViewById<TextView>(R.id.dialogTitle)
        val dialogMessage = customView.findViewById<TextView>(R.id.dialogMessage)
        val btnCancel = customView.findViewById<MaterialButton>(R.id.btnCancel)
        val btnDelete = customView.findViewById<MaterialButton>(R.id.btnDelete)

        // 3. Создание AlertDialog, используя пользовательский View
        val alertDialog = AlertDialog.Builder(this)
            .setView(customView) // Установка пользовательского макета
            .create() // Создание диалога

        // 4. Установка прозрачного фона для самого AlertDialog, чтобы rounded_dialog_background был виден
        // Важно, чтобы не было стандартного белого прямоугольника под фоном.
        alertDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        // 5. Установка текста (если нужно, можно динамически изменять)
        dialogTitle.text = "Подтвердите удаление"
        dialogMessage.text = "Вы уверены, что хотите удалить эту запись? Действие необратимо."

        // 6. Привязка обработчиков нажатий к кнопкам в пользовательском макете
        btnDelete.setOnClickListener {
            cancelRecording() // Вызов существующей функции для удаления
            alertDialog.dismiss() // Закрытие диалога
        }

        btnCancel.setOnClickListener {
            alertDialog.dismiss() // Простое закрытие диалога
        }

        // 7. Показ диалога
        alertDialog.show()
    }
        
    private fun cancelRecording() {
        if (!isRecording) return

        // 1. Остановка и удаление файла
        stopRecording(shouldDeleteFile = true)

        // --- остановка таймера и сброс счетчика ---
        timerHandler.removeCallbacks(timerRunnable) 
        secondsElapsed = 0 
        
        // Сброс иконки
        pauseResumeButton.setIconResource(R.drawable.ic_pause_24) 

        // 2. Сброс UI в исходное состояние
        isRecording = false
        isPaused = false
        
        // Переключение UI, сокрытие панели управления, показ микрофона и файла
        recordingControlsLayout.visibility = View.GONE
        recordButton.visibility = View.VISIBLE
        selectFileButton.visibility = View.GONE
        
        outputTextView.text = "Запись отменена."
        processButton.visibility = View.GONE
        reverseButton.visibility = View.GONE
    }    

    override fun onBackPressed() {
        // (Кнопка назад у смартфона)Стандартные функции
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }
    }