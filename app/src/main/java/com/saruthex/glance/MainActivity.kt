package com.saruthex.glance

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.*
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.sqrt

private val BG=Color(0xFF090B10)
private val PANEL=Color(0xFF11161F)
private val ACCENT=Color(0xFF8AB4FF)

class MainActivity:ComponentActivity(){
    private var granted by mutableStateOf(false)
    private val ask=registerForActivityResult(ActivityResultContracts.RequestPermission()){granted=it}
    override fun onCreate(s:Bundle?){super.onCreate(s);enableEdgeToEdge()
        granted=ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED
        if(!granted) ask.launch(Manifest.permission.CAMERA)
        setContent{GlanceApp(granted)}
    }
}

data class Sig(val v:List<Float>){
    fun distance(o:Sig)=if(v.size!=o.v.size) 999f else sqrt(v.indices.sumOf{val d=(v[it]-o.v[it]).toDouble();d*d}).toFloat()/v.size
    fun json()=JSONArray().also{a->v.forEach{a.put(it.toDouble())}}
    companion object{
        fun from(f:Face):Sig{
            val b=f.boundingBox; val w=b.width().coerceAtLeast(1).toFloat();val h=b.height().coerceAtLeast(1).toFloat()
            fun px(t:Int)=f.getLandmark(t)?.position?.x?.minus(b.left)?.div(w)?:.5f
            fun py(t:Int)=f.getLandmark(t)?.position?.y?.minus(b.top)?.div(h)?:.5f
            return Sig(listOf(w/h,f.headEulerAngleY/45f,f.headEulerAngleZ/45f,
                px(FaceLandmark.LEFT_EYE),py(FaceLandmark.LEFT_EYE),px(FaceLandmark.RIGHT_EYE),py(FaceLandmark.RIGHT_EYE),
                px(FaceLandmark.NOSE_BASE),py(FaceLandmark.NOSE_BASE),px(FaceLandmark.MOUTH_BOTTOM),py(FaceLandmark.MOUTH_BOTTOM)))
        }
        fun parse(a:JSONArray)=Sig(List(a.length()){a.getDouble(it).toFloat()})
    }
}
data class Profile(val name:String,val samples:List<Sig>)
class Store(c:Context){
    private val p=c.getSharedPreferences("glance",0)
    fun load():Profile?=runCatching{val o=JSONObject(p.getString("profile",null)?:return null);val a=o.getJSONArray("s");Profile(o.getString("n"),List(a.length()){Sig.parse(a.getJSONArray(it))})}.getOrNull()
    fun save(x:Profile){val a=JSONArray();x.samples.forEach{a.put(it.json())};p.edit().putString("profile",JSONObject().put("n",x.name).put("s",a).toString()).apply()}
    fun clear(){p.edit().clear().apply()}
}

@Composable fun GlanceApp(granted:Boolean){
    val c=LocalContext.current;val store=remember{Store(c)};var profile by remember{mutableStateOf(store.load())};var tab by remember{mutableIntStateOf(0)}
    MaterialTheme(colorScheme=darkColorScheme(primary=ACCENT,background=BG,surface=PANEL)){
        Scaffold(containerColor=BG,bottomBar={NavigationBar(containerColor=PANEL){
            listOf("Glance","Enroll","Faces","Settings").forEachIndexed{i,n->NavigationBarItem(selected=tab==i,onClick={tab=i},icon={Text(if(tab==i)"●"else"○")},label={Text(n)})}
        }}){pad->Box(Modifier.fillMaxSize().padding(pad)){when(tab){
            0->Scan(granted,profile)
            1->Enroll(granted,profile?.name){store.save(it);profile=it;tab=2}
            2->Faces(profile){store.clear();profile=null}
            else->Settings(profile!=null){store.clear();profile=null}
        }}}
    }
}

@Composable fun Scan(granted:Boolean,profile:Profile?){
    var running by remember{mutableStateOf(false)};var faces by remember{mutableStateOf<List<Face>>(emptyList())}
    var blink by remember{mutableStateOf(false)};var left by remember{mutableStateOf(false)};var right by remember{mutableStateOf(false)}
    val face=faces.singleOrNull()
    if(face!=null){if((face.leftEyeOpenProbability?:1f)<.35f&&(face.rightEyeOpenProbability?:1f)<.35f)blink=true;if(face.headEulerAngleY>15)left=true;if(face.headEulerAngleY< -15)right=true}
    val d=if(face!=null&&profile!=null)profile.samples.minOfOrNull{it.distance(Sig.from(face))}else null
    val ok=d!=null&&d<.09f&&blink&&left&&right
    Column(Modifier.fillMaxSize().padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally){
        Spacer(Modifier.height(16.dp));Text("GLANCE",color=ACCENT,fontWeight=FontWeight.Bold);Text("Live face verification",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold);Spacer(Modifier.height(20.dp))
        if(running&&granted)Camera(Modifier.size(300.dp).clip(CircleShape)){faces=it}else Surface(Modifier.size(300.dp),shape=CircleShape,color=PANEL){Box(contentAlignment=Alignment.Center){Text(if(profile==null)"Enroll a profile first"else"Ready to scan")}}
        Spacer(Modifier.height(18.dp))
        val status=when{!granted->"Camera permission required";profile==null->"Open Enroll and create your profile";!running->"Tap Start verification";face==null->"Looking for one face…";faces.size>1->"Use one face only";ok->"✓ Verified as "+profile.name;!blink->"Liveness: blink once";!left->"Liveness: turn your head left";!right->"Liveness: turn your head right";d!=null&&d<.09f->"Face match found";else->"Face does not match profile"}
        Text(status,color=if(ok)Color(0xFF78D9A5)else Color.LightGray)
        Spacer(Modifier.weight(1f));Button({running=!running;if(running){blink=false;left=false;right=false}},enabled=granted&&profile!=null,modifier=Modifier.fillMaxWidth().height(56.dp)){Text(if(running)"Stop camera"else"Start verification")}
        Spacer(Modifier.height(8.dp));Text("On-device prototype • not a replacement for Android biometrics",style=MaterialTheme.typography.labelSmall,color=Color.Gray)
    }
}

@Composable fun Enroll(granted:Boolean,old:String?,save:(Profile)->Unit){
    var name by remember{mutableStateOf(old?:"")};var faces by remember{mutableStateOf<List<Face>>(emptyList())};var samples by remember{mutableStateOf<List<Sig>>(emptyList())}
    Column(Modifier.fillMaxSize().padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally){
        Spacer(Modifier.height(16.dp));Text("Enroll your face",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold);Text("Capture five natural positions.",color=Color.LightGray)
        Spacer(Modifier.height(14.dp));OutlinedTextField(name,{name=it},label={Text("Profile name")},singleLine=true);Spacer(Modifier.height(16.dp))
        if(granted)Camera(Modifier.size(260.dp).clip(CircleShape)){faces=it}else Text("Camera permission required")
        Spacer(Modifier.height(14.dp));Text("Samples: "+samples.size+"/5",color=ACCENT);val face=faces.singleOrNull();Text(if(face==null)"Position one face in the frame"else"Face detected • capture when ready",color=Color.LightGray)
        Spacer(Modifier.weight(1f));Button({if(face!=null&&samples.size<5)samples=samples+Sig.from(face)},enabled=face!=null&&samples.size<5&&name.isNotBlank(),modifier=Modifier.fillMaxWidth()){Text("Capture sample")}
        Spacer(Modifier.height(8.dp));Button({save(Profile(name.trim(),samples))},enabled=samples.size>=5&&name.isNotBlank(),modifier=Modifier.fillMaxWidth()){Text("Save profile")}
    }
}

@Composable fun Faces(profile:Profile?,del:()->Unit){
    var confirm by remember{mutableStateOf(false)};Column(Modifier.fillMaxSize().padding(24.dp)){Spacer(Modifier.height(20.dp));Text("Saved identities",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold);Spacer(Modifier.height(20.dp))
        if(profile==null)Text("No profile enrolled yet.",color=Color.LightGray)else{Text(profile.name,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold);Text(profile.samples.size.toString()+" enrollment samples stored locally",color=Color.LightGray);Spacer(Modifier.height(20.dp));OutlinedButton({confirm=true}){Text("Delete profile")}}}
    if(confirm)AlertDialog(onDismissRequest={confirm=false},title={Text("Delete profile?")},text={Text("This removes local enrollment data.")},confirmButton={TextButton(onClick={del();confirm=false}){Text("Delete")}},dismissButton={TextButton(onClick={confirm=false}){Text("Cancel")}})
}

@Composable fun Settings(has:Boolean,clear:()->Unit){
    var live by remember{mutableStateOf(true)};var confirm by remember{mutableStateOf(false)}
    Column(Modifier.fillMaxSize().padding(24.dp)){Spacer(Modifier.height(20.dp));Text("Settings",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold);Spacer(Modifier.height(20.dp))
        Row(verticalAlignment=Alignment.CenterVertically,modifier=Modifier.fillMaxWidth()){Column(Modifier.weight(1f)){Text("Liveness challenge");Text("Blink and turn your head",color=Color.LightGray)};Switch(live,{live=it})}
        Spacer(Modifier.height(20.dp));Text("Privacy",fontWeight=FontWeight.Bold);Text("Enrollment samples stay in the app's local storage in this build.",color=Color.LightGray);Spacer(Modifier.height(24.dp))
        if(has)OutlinedButton({confirm=true},modifier=Modifier.fillMaxWidth()){Text("Delete all local data")};Text("Version 1.0 • Galaxy S22",color=Color.Gray)
    }
    if(confirm)AlertDialog(onDismissRequest={confirm=false},title={Text("Delete all data?")},text={Text("Your local profile will be removed.")},confirmButton={TextButton(onClick={clear();confirm=false}){Text("Delete")}},dismissButton={TextButton(onClick={confirm=false}){Text("Cancel")}})
}

@Composable fun Camera(mod:Modifier,onFaces:(List<Face>)->Unit){
    val c=LocalContext.current;val owner=LocalLifecycleOwner.current;val view=remember{PreviewView(c).apply{scaleType=PreviewView.ScaleType.FILL_CENTER}}
    DisposableEffect(owner){val future=ProcessCameraProvider.getInstance(c);val ex=ContextCompat.getMainExecutor(c);future.addListener({
        val provider=future.get();val preview=Preview.Builder().build().also{it.surfaceProvider=view.surfaceProvider}
        val detector=FaceDetection.getClient(FaceDetectorOptions.Builder().setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE).setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL).setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL).build())
        val analysis=ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
        analysis.setAnalyzer(ex){p->val image=p.image;if(image==null)p.close()else detector.process(InputImage.fromMediaImage(image,p.imageInfo.rotationDegrees)).addOnSuccessListener{onFaces(it)}.addOnFailureListener{onFaces(emptyList())}.addOnCompleteListener{p.close()}}
        try{provider.unbindAll();provider.bindToLifecycle(owner,CameraSelector.DEFAULT_FRONT_CAMERA,preview,analysis)}catch(_:Exception){onFaces(emptyList())}
    },ex);onDispose{try{ProcessCameraProvider.getInstance(c).get().unbindAll()}catch(_:Exception){}}}
    AndroidView({view},modifier=mod)
}