package com.example.b2g

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.util.Locale

class MainActivity : ComponentActivity() {
  private var tts: TextToSpeech? = null
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    tts = TextToSpeech(this) { status ->
      if (status == TextToSpeech.SUCCESS) {
        tts?.language = Locale.GERMAN
        val male = tts?.voices?.firstOrNull { v -> v.locale.language=="de" && v.name.lowercase().contains("male") }
        if (male!=null) tts?.voice = male
      }
    }
    setContent { MaterialTheme { AppRoot(tts) } }
  }
  override fun onDestroy() { tts?.stop(); tts?.shutdown(); super.onDestroy() }
}

sealed class Screen { object Home: Screen(); object Category: Screen(); object Lesson: Screen(); object Review: Screen(); object Curriculum: Screen(); object B1Exam: Screen(); object ExamReport: Screen() }

@kotlinx.serialization.Serializable data class Lesson(val id:String,val title:String,val level:String,val steps:List<Step>)
@kotlinx.serialization.Serializable data class Step(val type:String,val promptBn:String,val promptDe:String?=null,val options:List<String>?=null,val answer:String?=null,val pairs:List<PairItem>?=null)
@kotlinx.serialization.Serializable data class PairItem(val bn:String,val de:String)
@kotlinx.serialization.Serializable data class Category(val level:String,val lessons:List<Lesson>)

@kotlinx.serialization.Serializable data class CurriculumStage(val stage:String, val goals:List<String>, val sampleTopics:List<String>, val checkpointLessonIds:List<String>)

@kotlinx.serialization.Serializable data class B1Exam(val reading: List<B1Q>, val listening: List<B1Q>, val writing: List<B1Task>, val speaking: List<B1Task>)
@kotlinx.serialization.Serializable data class B1Q(val prompt:String, val options: List<String>, val answer: String)
@kotlinx.serialization.Serializable data class B1Task(val prompt:String, val keywords: List<String>, val minWords:Int)

@Composable
fun AppRoot(tts: TextToSpeech?) {
  val ctx = LocalContext.current
  val prefs = ctx.getSharedPreferences("b2g_prefs", Context.MODE_PRIVATE)
  var screen by remember { mutableStateOf<Screen>(Screen.Home) }
  var selected by remember { mutableStateOf<Category?>(null) }
  var lesson by remember { mutableStateOf<Lesson?>(null) }
  var examResult by remember { mutableStateOf<String?>(null) }

  when (screen) {
    is Screen.Home -> HomeScreen(
      prefs = prefs,
      onOpen = { c -> selected = c; screen = Screen.Category },
      onOpenReview = { screen = Screen.Review },
      onOpenCurriculum = { screen = Screen.Curriculum },
      onOpenB1 = { screen = Screen.B1Exam }
    )
    is Screen.Category -> CategoryScreen(category = selected, onBack = { screen = Screen.Home }, onOpenLesson = { l -> lesson = l; screen = Screen.Lesson })
    is Screen.Lesson -> LessonScreen(lesson!!, onBack = { screen = Screen.Category }, tts = tts, prefs = prefs)
    is Screen.Review -> ReviewScreen(prefs = prefs, onBack = { screen = Screen.Home })
    is Screen.Curriculum -> CurriculumScreen(onBack = { screen = Screen.Home })
    is Screen.B1Exam -> B1ExamScreen(prefs = prefs, tts = tts, onDone = { report -> examResult = report; screen = Screen.ExamReport })
    is Screen.ExamReport -> ExamReportScreen(report = examResult ?: "No report", onBack = { screen = Screen.Home })
  }
}

@Composable
fun HomeScreen(prefs: SharedPreferences, onOpen:(Category)->Unit, onOpenReview:()->Unit, onOpenCurriculum:()->Unit, onOpenB1:()->Unit) {
  val ctx = LocalContext.current
  val json = ctx.assets.open("b2g_lessons.json").bufferedReader().use{it.readText()}
  val data = Json{ ignoreUnknownKeys=true }.decodeFromString<List<Category>>(json)
  val xp = prefs.getInt("xp",0)
  val streak = prefs.getInt("streak",0)
  Scaffold(topBar={ TopAppBar(title={ Text("B2G German • XP: $xp • 🔥 $streak") })}){pv->
    Column(Modifier.padding(pv).padding(12.dp)) {
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        Button(onClick = onOpenReview) { Text("🔁 Review") }
        Button(onClick = onOpenCurriculum) { Text("📚 Curriculum") }
        Button(onClick = onOpenB1) { Text("🎓 B1 Mock Exam") }
      }
      LazyColumn {
        items(data){cat->
          Card(Modifier.padding(12.dp).fillMaxWidth().clickable{ onOpen(cat) }){
            Column(Modifier.padding(16.dp)) {
              Text("Level: ${cat.level}", style=MaterialTheme.typography.titleLarge, fontWeight=FontWeight.Bold)
              Text("Lessons: ${cat.lessons.size}")
            }
          }
        }
      }
    }
  }
}

@Composable
fun CurriculumScreen(onBack:()->Unit) {
  val ctx = LocalContext.current
  val json = ctx.assets.open("curriculum.json").bufferedReader().use{it.readText()}
  val stages = kotlinx.serialization.json.Json.decodeFromString<List<CurriculumStage>>(json)
  Scaffold(topBar = { TopAppBar(title={Text("A1 → A2 → B1 → B2 পথনকশা")}, navigationIcon={ Text("< Back", modifier=Modifier.padding(16.dp).clickable{onBack()}) }) }) { pv ->
    LazyColumn(Modifier.padding(pv)) {
      items(stages){st->
        Card(Modifier.padding(12.dp).fillMaxWidth()) {
          Column(Modifier.padding(16.dp)) {
            Text(st.stage, style=MaterialTheme.typography.titleLarge, fontWeight=FontWeight.Bold)
            Text("Goals:"); st.goals.forEach{ g-> Text("• "+g) }
            Spacer(Modifier.height(8.dp)); Text("Sample Topics:"); st.sampleTopics.forEach{ t-> Text("• "+t) }
          }
        }
      }
    }
  }
}

@Composable
fun B1ExamScreen(prefs: SharedPreferences, tts: TextToSpeech?, onDone:(String)->Unit) {
  val ctx = LocalContext.current
  val json = ctx.assets.open("b1_mock.json").bufferedReader().use{it.readText()}
  val exam = kotlinx.serialization.json.Json.decodeFromString<B1Exam>(json)

  var rScore by remember { mutableStateOf(0) }
  var lScore by remember { mutableStateOf(0) }
  var wScore by remember { mutableStateOf(0) }
  var sScore by remember { mutableStateOf(0) }
  var page by remember { mutableStateOf(0) }

  fun finalize() {
    val total = rScore + lScore + wScore + sScore
    val suggestion = if (total >= 60 && minOf(rScore,lScore,wScore,sScore) >= 10) "✅ Likely Pass (B1 range)" else "⚠️ Borderline/Below B1"
    val report = "B1 Mock Result\nReading: $rScore/25\nListening: $lScore/25\nWriting: $wScore/25\nSpeaking: $sScore/25\nTotal: $total/100\n$suggestion"
    onDone(report)
  }

  Scaffold(topBar={ TopAppBar(title={ Text("B1 Mock Exam") })}) { pv ->
    Column(Modifier.padding(pv).padding(12.dp)) {
      when(page){
        0 -> {
          Text("Reading (25)", style=MaterialTheme.typography.titleLarge, fontWeight=FontWeight.Bold)
          var score by remember { mutableStateOf(0) }
          exam.reading.forEach { q ->
            var chosen by remember { mutableStateOf<String?>(null) }
            Text(q.prompt, fontWeight=FontWeight.SemiBold, modifier=Modifier.padding(top=8.dp,bottom=4.dp))
            q.options.forEach { opt ->
              Button(onClick={ chosen = opt; }, modifier=Modifier.fillMaxWidth().padding(vertical=3.dp)) { Text(opt) }
            }
            if (chosen!=null) {
              if (chosen==q.answer) score += 5
            }
          }
          Button(onClick={ rScore = minOf(score,25); page = 1 }, modifier=Modifier.padding(top=12.dp)) { Text("Next: Listening") }
        }
        1 -> {
          Text("Listening (25)", style=MaterialTheme.typography.titleLarge, fontWeight=FontWeight.Bold)
          var score by remember { mutableStateOf(0) }
          exam.listening.forEach { q ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween) {
              Button(onClick={ tts?.speak(q.prompt, TextToSpeech.QUEUE_FLUSH, null, "listen_b1") }){ Text("▶ Play DE") }
            }
            var chosen by remember { mutableStateOf<String?>(null) }
            q.options.forEach { opt -> Button(onClick={ chosen=opt }, modifier=Modifier.fillMaxWidth().padding(vertical=3.dp)) { Text(opt) } }
            if (chosen!=null) if (chosen==q.answer) score += 5
          }
          Button(onClick={ lScore = minOf(score,25); page = 2 }, modifier=Modifier.padding(top=12.dp)) { Text("Next: Writing") }
        }
        2 -> {
          Text("Writing (25)", style=MaterialTheme.typography.titleLarge, fontWeight=FontWeight.Bold)
          var text by remember { mutableStateOf("") }
          val task = exam.writing.first()
          Text(task.prompt); Spacer(Modifier.height(8.dp))
          OutlinedTextField(value=text, onValueChange={text=it}, modifier=Modifier.fillMaxWidth().height(180.dp), label={Text("আপনার উত্তর (DE)")})
          Button(onClick={ wScore = evaluateText(text, task.keywords, task.minWords); page = 3 }, modifier=Modifier.padding(top=12.dp)) { Text("Next: Speaking") }
        }
        3 -> {
          Text("Speaking (25)", style=MaterialTheme.typography.titleLarge, fontWeight=FontWeight.Bold)
          val task = exam.speaking.first()
          SpeakingPanel(task){ transcript ->
            sScore = evaluateText(transcript, task.keywords, task.minWords)
            finalize()
          }
        }
      }
    }
  }
}

@Composable
fun ExamReportScreen(report:String, onBack:()->Unit){
  Scaffold(topBar={ TopAppBar(title={Text("Exam Report")}, navigationIcon={ Text("< Back", modifier=Modifier.padding(16.dp).clickable{onBack()}) }) }){pv->
    Column(Modifier.padding(pv).padding(16.dp)) {
      Text(report)
    }
  }
}

fun evaluateText(text:String, keywords:List<String>, minWords:Int): Int {
  val words = text.trim().split(Regex("\s+")).filter{it.isNotBlank()}
  val lengthScore = when {
    words.size >= minWords+40 -> 10
    words.size >= minWords+20 -> 8
    words.size >= minWords -> 6
    words.size >= minWords/2 -> 3
    else -> 0
  }
  val lower = text.lowercase()
  var hit = 0
  for (k in keywords){
    if (lower.contains(k.lowercase())) hit += 1
  }
  val keywordScore = (hit * 2).coerceAtMost(8)
  val connectors = listOf("weil","deshalb","obwohl","zuerst","danach","schließlich","aber","und","oder","denn","dass")
  val connHit = connectors.count { lower.contains(it) }
  val cohesionScore = (connHit).coerceAtMost(7)
  val total = (lengthScore + keywordScore + cohesionScore).coerceAtMost(25)
  return total
}

@Composable
fun SpeakingPanel(task: B1Task, onResult:(String)->Unit){
  val ctx = LocalContext.current
  var heard by remember { mutableStateOf("") }
  var listening by remember { mutableStateOf(false) }
  val hasPerm = ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED
  val launcher = androidx.activity.compose.rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){}
  Text(task.prompt)
  Spacer(Modifier.height(8.dp))
  Text("কীওয়ার্ড গাইড: "+task.keywords.joinToString(", "))
  Spacer(Modifier.height(8.dp))
  Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceEvenly) {
    Button(onClick={ if(!hasPerm) launcher.launch(Manifest.permission.RECORD_AUDIO) else { startListening(ctx){ txt-> heard = txt; listening=false }; listening=true } }){ Text(if(listening) "🎙️ Listening..." else "🎙️ Speak") }
    Button(onClick={ onResult(heard) }){ Text("Finish") }
  }
  if(heard.isNotBlank()){ Spacer(Modifier.height(8.dp)); Text("Transcript: "+heard) }
}

@Composable
fun CategoryScreen(category: Category?, onBack:()->Unit, onOpenLesson:(Lesson)->Unit){
  if (category==null){ onBack(); return }
  Scaffold(topBar={ TopAppBar(title={Text("${category.level} Lessons")}, navigationIcon={ Text("< Back", modifier=Modifier.padding(16.dp).clickable{onBack()}) }) }){pv->
    LazyColumn(Modifier.padding(pv)){
      items(category.lessons){l->
        Card(Modifier.padding(12.dp).fillMaxWidth().clickable{ onOpenLesson(l) }){ Column(Modifier.padding(16.dp)){ Text(l.title, style=MaterialTheme.typography.titleMedium, fontWeight=FontWeight.Bold); Text("Steps: ${l.steps.size}") } }
      }
    }
  }
}

@Composable
fun LessonScreen(lesson: Lesson, onBack:()->Unit, tts: TextToSpeech?, prefs: SharedPreferences){
  var stepIdx by remember{ mutableStateOf(0) }
  var score by remember{ mutableStateOf(0) }
  val step = lesson.steps[stepIdx]
  val total = lesson.steps.size
  Scaffold(topBar={ TopAppBar(title={ Text("${lesson.title} • ${stepIdx+1}/$total") }, navigationIcon={ Text("< Back", modifier=Modifier.padding(16.dp).clickable{onBack()}) }) }, bottomBar={ LinearProgressIndicator(progress=(stepIdx+1f)/total, modifier=Modifier.fillMaxWidth()) }){pv->
    Column(Modifier.padding(pv).padding(16.dp), horizontalAlignment=Alignment.CenterHorizontally){
      when(step.type){
        "mcq" -> McqStep(step){ ok -> if(ok)score++ else addToReview(prefs, step.promptBn, step.answer ?: step.promptDe ?: "", 0); nextOrFinish(prefs, lesson, stepIdx, total, score, onBack){ stepIdx++ } }
        "match" -> MatchStep(step){ ok -> if(ok)score++ else addToReview(prefs, step.promptBn, step.pairs?.firstOrNull()?.de ?: "", 0); nextOrFinish(prefs, lesson, stepIdx, total, score, onBack){ stepIdx++ } }
        "listen" -> ListenStep(step, tts){ nextOrFinish(prefs, lesson, stepIdx, total, score, onBack){ stepIdx++ } }
        "typein" -> TypeInStep(step){ ok,typed -> if(ok)score++ else addToReview(prefs, step.promptBn, step.answer ?: "", 0); nextOrFinish(prefs, lesson, stepIdx, total, score, onBack){ stepIdx++ } }
        "speak" -> SpeakStep(step){ ok, heard -> if(ok)score++ else addToReview(prefs, step.promptBn, step.answer ?: (step.promptDe ?: ""), 0); nextOrFinish(prefs, lesson, stepIdx, total, score, onBack){ stepIdx++ } }
        else -> Text("Unsupported step")
      }
    }
  }
}

fun nextOrFinish(prefs: SharedPreferences, lesson: Lesson, idx:Int, total:Int, score:Int, onBack:()->Unit, advance:()->Unit){
  if(idx < total-1) advance() else {
    prefs.edit().putInt("done_${lesson.id}", score).apply()
    val xp = prefs.getInt("xp", 0) + 10
    prefs.edit().putInt("xp", xp).apply()
    val today = java.time.LocalDate.now().toString()
    val last = prefs.getString("last_day", null)
    val streak = if (last==today || last==java.time.LocalDate.now().minusDays(1).toString()) prefs.getInt("streak",0)+1 else 1
    prefs.edit().putInt("streak", streak).putString("last_day", today).apply()
    onBack()
  }
}

fun addToReview(prefs: SharedPreferences, promptBn:String, correct:String, level:Int){
  val old = prefs.getString("review_pool","")?:""
  val entry = "${level}:${promptBn.replace("|","/")}||${correct.replace("|","/")}"
  val updated = if(old.isBlank()) entry else old + ";;" + entry
  prefs.edit().putString("review_pool", updated).apply()
}

@Composable
fun ReviewScreen(prefs: SharedPreferences, onBack:()->Unit){
  val poolRaw = prefs.getString("review_pool","")?:""
  val items = if(poolRaw.isBlank()) emptyList() else poolRaw.split(";;").mapNotNull{
    val seg = it.split("||"); if(seg.size!=2) null else {
      val meta = seg[0].split(":"); if(meta.size!=2) null else Triple(meta[0].toIntOrNull()?:0, meta[1], seg[1])
    }
  }.sortedBy{it.first}
  Scaffold(topBar={ TopAppBar(title={Text("Review (${items.size})")}, navigationIcon={ Text("< Back", modifier=Modifier.padding(16.dp).clickable{onBack()}) }, actions={ Text("Clear", modifier=Modifier.padding(16.dp).clickable{ prefs.edit().remove("review_pool").apply() }) }) }){pv->
    if(items.isEmpty()) Column(Modifier.padding(pv).padding(24.dp), horizontalAlignment=Alignment.CenterHorizontally){ Text("এখন কিছু রিভিউ বাকি নেই 🎉") }
    else LazyColumn(Modifier.padding(pv)){ items(items){ triple -> ReviewItem(level=triple.first, promptBn=triple.second, correct=triple.third, prefs=prefs) } }
  }
}

@Composable
fun ReviewItem(level:Int, promptBn:String, correct:String, prefs: SharedPreferences){
  var typed by remember{ mutableStateOf("") }
  Card(Modifier.padding(12.dp).fillMaxWidth()){
    Column(Modifier.padding(16.dp)){
      Text("[L$level] $promptBn", style=MaterialTheme.typography.titleMedium, fontWeight=FontWeight.Bold)
      OutlinedTextField(value=typed, onValueChange={typed=it}, label={Text("জার্মান টাইপ করুন")}, modifier=Modifier.fillMaxWidth())
      Spacer(Modifier.height(8.dp))
      val right = typed.trim().lowercase()==correct.trim().lowercase()
      Row(horizontalArrangement=Arrangement.SpaceBetween, modifier=Modifier.fillMaxWidth()){
        Button(onClick={ if(right){ if(level>=2) removeFromPool(prefs, promptBn, correct) else promoteInPool(prefs, promptBn, correct) } }){ Text(if(right) "✅ ঠিক (প্রমোট/রিমুভ)" else "চেক") }
        Text("সঠিক: "+correct)
      }
    }
  }
}

fun removeFromPool(prefs: SharedPreferences, promptBn:String, correct:String){
  val pool = prefs.getString("review_pool","")?:""
  val parts = pool.split(";;").toMutableList()
  val targetSuffix = "||${correct.replace("|","/")}"
  val newParts = parts.filterNot{ it.contains("${promptBn.replace("|","/")}${targetSuffix}") }
  val updated = newParts.filter{ it.isNotBlank() }.joinToString(";;")
  prefs.edit().putString("review_pool", updated).apply()
}
fun promoteInPool(prefs: SharedPreferences, promptBn:String, correct:String){
  val pool = prefs.getString("review_pool","")?:""
  val parts = pool.split(";;").toMutableList()
  for(i in parts.indices){
    val it = parts[i]
    val seg = it.split("||")
    if(seg.size==2){
      val meta = seg[0].split(":")
      if(meta.size==2){
        val lvl = meta[0].toIntOrNull()?:0
        val q = meta[1]
        if(q==promptBn){
          val newlvl = (lvl+1).coerceAtMost(2)
          parts[i] = f"{newlvl}:{q}||{seg[1]}"
          break
        }
      }
    }
  }
  val updated = ";;".join([p for p in parts if p.strip()])
  prefs.edit().putString("review_pool", updated).apply()
}

@Composable
fun McqStep(step: Step, onNext:(Boolean)->Unit){
  Text(step.promptBn, style=MaterialTheme.typography.titleLarge, fontWeight=FontWeight.Bold)
  Spacer(Modifier.height(12.dp))
  step.options?.forEach{opt-> Button(onClick={ onNext(opt==step.answer) }, modifier=Modifier.fillMaxWidth().padding(vertical=3.dp)){ Text(opt) } }
}

@Composable
fun ListenStep(step: Step, tts: TextToSpeech?, onNext:()->Unit){
  Text(step.promptBn, style=MaterialTheme.typography.titleLarge, fontWeight=FontWeight.Bold)
  Spacer(Modifier.height(12.dp))
  Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceEvenly){
    Button(onClick={ val text = step.promptDe?:""; tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "de_tts") }){ Text("▶ শোনো (DE)") }
    Button(onClick=onNext){ Text("পরেরটি") }
  }
}

@Composable
fun MatchStep(step: Step, onNext:(Boolean)->Unit){
  val pairs = step.pairs?: emptyList()
  Column{
    Text("বাংলা → জার্মান মিলাও", style=MaterialTheme.typography.titleLarge, fontWeight=FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
    pairs.forEach{p->
      Text(p.bn, fontWeight=FontWeight.SemiBold, modifier=Modifier.padding(vertical=4.dp))
      Row{ Button(onClick={ onNext(true) }, modifier=Modifier.padding(4.dp)){ Text(p.de) } }
      Spacer(Modifier.height(6.dp))
    }
  }
}

@Composable
fun TypeInStep(step: Step, onNext:(Boolean,String)->Unit){
  var typed by remember{ mutableStateOf("") }
  Text(step.promptBn, style=MaterialTheme.typography.titleLarge, fontWeight=FontWeight.Bold)
  Spacer(Modifier.height(12.dp))
  OutlinedTextField(value=typed, onValueChange={typed=it}, label={Text("জার্মান টাইপ করুন")}, modifier=Modifier.fillMaxWidth())
  Spacer(Modifier.height(8.dp))
  val ok = typed.trim().lowercase()==(step.answer?:"").trim().lowercase()
  Button(onClick={ onNext(ok, typed) }, modifier=Modifier.fillMaxWidth()){ Text("চেক") }
}

@Composable
fun SpeakStep(step: Step, onNext:(Boolean,String)->Unit){
  val ctx = LocalContext.current
  var heard by remember{ mutableStateOf("") }
  var listening by remember{ mutableStateOf(false) }
  val hasPerm = ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED
  val launcher = androidx.activity.compose.rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
  Text(step.promptBn, style=MaterialTheme.typography.titleLarge, fontWeight=FontWeight.Bold)
  Spacer(Modifier.height(8.dp))
  Text("উচ্চারণ বলুন: "+(step.answer ?: step.promptDe ?: ""))
  Spacer(Modifier.height(8.dp))
  Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceEvenly){
    Button(onClick={ if(!hasPerm) launcher.launch(Manifest.permission.RECORD_AUDIO) else { startListening(ctx){ txt-> heard=txt; listening=false }; listening=true } }){ Text(if(listening) "🎙️ শোনা হচ্ছে..." else "🎙️ বলুন") }
    Button(onClick={ val target=(step.answer ?: step.promptDe ?: "").trim().lowercase(); val ok = heard.trim().lowercase()==target; onNext(ok, heard) }){ Text("চেক") }
  }
  if(heard.isNotBlank()){ Spacer(Modifier.height(8.dp)); Text("শুনেছি: "+heard) }
}

fun startListening(ctx: Context, onResult:(String)->Unit){
  val rec = SpeechRecognizer.createSpeechRecognizer(ctx)
  val intent = android.content.Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply{
    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "de-DE")
    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
  }
  rec.setRecognitionListener(object: RecognitionListener{
    override fun onReadyForSpeech(p0: Bundle?) {}
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(p0: Float) {}
    override fun onBufferReceived(p0: ByteArray?) {}
    override fun onEndOfSpeech() { rec.stopListening(); rec.cancel(); rec.destroy() }
    override fun onError(p0: Int) { rec.destroy() }
    override fun onResults(b: Bundle) {
      val list = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
      onResult(list?.firstOrNull() ?: "")
    }
    override fun onPartialResults(p0: Bundle) {}
    override fun onEvent(p0: Int, p1: Bundle?) {}
  })
  rec.startListening(intent)
}
