package com.akira.app.feature.settings

import android.media.AudioDeviceInfo
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.akira.app.core.audio.*
import com.akira.app.core.datastore.*
import com.akira.app.core.design.components.*
import com.akira.app.core.design.icon.AkiraIcons
import com.akira.app.core.i18n.LocalStrings
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.log10
import kotlin.math.pow

@Composable internal fun OutputProfilesSheet(onDismiss: () -> Unit) {
    val context=LocalContext.current
    val repo=remember(context) {UserPreferencesRepository(context)}
    val prefs by repo.userPreferences.collectAsStateWithLifecycle(UserPreferences())
    val output by OutputRoutes.current.collectAsStateWithLifecycle()
    val profiles=remember(prefs.eqProfilesJson) {OutputProfiles.decode(prefs.eqProfilesJson)}
    val assignments=remember(prefs.eqAssignmentsJson) {OutputProfiles.assignments(prefs.eqAssignmentsJson)}
    val effective=remember(prefs,output) {prefs.effectiveEqualizer(output)}
    val strings=LocalStrings.current
    val scope=rememberCoroutineScope()
    var naming by remember {mutableStateOf(false)}
    var rename by remember {mutableStateOf<EqOutputProfile?>(null)}
    var editing by remember {mutableStateOf<EqOutputProfile?>(null)}
    var deleting by remember {mutableStateOf<EqOutputProfile?>(null)}
    var menu by remember {mutableStateOf<String?>(null)}
    var saving by remember {mutableStateOf(false)}
    var snapshot by remember {mutableStateOf(EqualizerConfiguration())}
    var saveOutput by remember {mutableStateOf(output)}
    var saveName by remember {mutableStateOf("")}
    val outputName=if(output.type==AudioDeviceInfo.TYPE_BUILTIN_SPEAKER)strings.deviceSpeaker
        else output.productName.ifBlank {strings.library.output}
    fun update(edit:(List<EqOutputProfile>,Map<String,String>)->Pair<List<EqOutputProfile>,Map<String,String>>) {
        if(saving)return
        saving=true
        scope.launch {
            try {repo.updateOutputProfiles(edit)}
            catch(cancel:kotlinx.coroutines.CancellationException) {throw cancel}
            catch(_:Exception) {AkiraNotices.show(strings.library.error)}
            finally {saving=false}
        }
    }
    val viewport=((148+(profiles.size.coerceAtLeast(1)+1)*74+if(prefs.eqEnabled)0 else 42).dp * LocalDensity.current.fontScale)
        .coerceAtMost((LocalConfiguration.current.screenHeightDp*.57f).dp)
    AkiraModalSheet(onDismissRequest={if(!saving)onDismiss()},title=strings.library.profiles) {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max=viewport),contentPadding=PaddingValues(start=20.dp,end=20.dp,bottom=12.dp),
            verticalArrangement=Arrangement.spacedBy(8.dp)) {
            item {
                Text(strings.library.profiles,style=MaterialTheme.typography.headlineSmall)
                Row(Modifier.fillMaxWidth().padding(top=18.dp,bottom=14.dp),verticalAlignment=Alignment.CenterVertically,
                    horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    Surface(shape=androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                        color=audioAccent().copy(alpha=.12f)) {
                        Box(Modifier.size(48.dp),contentAlignment=Alignment.Center) {
                            Icon(if(output.type==AudioDeviceInfo.TYPE_BUILTIN_SPEAKER)Icons.Rounded.Speaker else Icons.Rounded.Headphones,
                                null,Modifier.size(24.dp),tint=audioAccent())
                        }
                    }
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        Text(outputName,style=MaterialTheme.typography.titleMedium)
                        Text(localizedText("Current audio output","Salida de audio actual"),
                            style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if(!prefs.eqEnabled) Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Text(localizedText("Equalizer off","Ecualizador apagado"),Modifier.weight(1f),
                        style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton({scope.launch {repo.setEqEnabled(true)}}) {Text(localizedText("Enable","Activar"),color=MaterialTheme.colorScheme.onSurface)}
                }
            }
            item {
                Text(localizedText("Choose a profile","Elige un perfil"),Modifier.padding(top=8.dp,bottom=8.dp),
                    style=MaterialTheme.typography.titleSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                ProfileChoiceRow(strings.library.globalProfile,localizedText("Use the general equalizer settings","Usar los ajustes generales del ecualizador"),
                    effective.profile==null,!saving,{update {list,mappings->list to (mappings-output.encoded)}})
            }
            items(profiles,key={it.id}) {profile->
                val selected=profile.id==effective.profile?.id
                val count=assignments.count {it.value==profile.id}
                val detail=when {
                    selected->localizedText("In use on $outputName","En uso en $outputName")
                    count==1->localizedText("Assigned to another output","Asignado a otra salida")
                    count>1->localizedText("Assigned to $count outputs","Asignado a $count salidas")
                    else->localizedText("Tap to use","Toca para usarlo")
                }
                ProfileChoiceRow(profile.name,detail,selected,!saving,
                    {update {list,mappings->list to (mappings+(output.encoded to profile.id))}},
                    trailing={AkiraIconButton(AkiraIcons.MoreHoriz,strings.options,{if(!saving)menu=profile.id},
                        tint=MaterialTheme.colorScheme.onSurface,showBackground=false)})
            }
            if(profiles.isEmpty())item {
                Text(localizedText("Save your current sound as a profile for this output.",
                    "Guarda tu sonido actual como un perfil para esta salida."),
                    Modifier.padding(top=18.dp,bottom=4.dp),style=MaterialTheme.typography.bodyMedium,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
            Box(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=8.dp)) {
                Button(onClick={
                    snapshot=effective.configuration;saveOutput=output;saveName=outputName;rename=null;menu=null;naming=true
                },enabled=!saving,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp),
                    colors=ButtonDefaults.buttonColors(containerColor=audioAccent(),
                        contentColor=if(audioAccent().luminance()>.179f)androidx.compose.ui.graphics.Color.Black else androidx.compose.ui.graphics.Color.White)) {
                    Icon(Icons.Rounded.Add,null,Modifier.size(19.dp));Spacer(Modifier.width(8.dp))
                    Text(localizedText("Save current settings","Guardar ajustes actuales"))
                }
            }
    }
    profiles.firstOrNull {it.id==menu}?.let {profile->
        AkiraContextMenu(onDismiss={menu=null},groups=listOf(listOf(
            AkiraContextAction(Icons.Rounded.GraphicEq,strings.editProfile,opensPresentation=true) {menu=null;editing=profile},
            AkiraContextAction(AkiraIcons.Airplay,strings.library.assignOutput,selected=effective.profile?.id==profile.id) {
                menu=null;update {list,mappings->list to (mappings+(output.encoded to profile.id))}
            },
            AkiraContextAction(AkiraIcons.Edit,strings.library.rename,opensPresentation=true) {menu=null;rename=profile;naming=true},
            AkiraContextAction(AkiraIcons.Queue,strings.library.duplicate) {menu=null;update {list,mappings->
                val latest=list.firstOrNull {it.id==profile.id} ?: profile
                (list+latest.copy(id=UUID.randomUUID().toString(),name="${latest.name} (${strings.library.duplicate})")) to mappings
            }}
        ),listOf(AkiraContextAction(AkiraIcons.Trash,strings.remove,destructive=true,opensPresentation=true) {menu=null;deleting=profile})))
    }
    if(naming)AkiraNameDialog(rename?.name ?: saveName,{naming=false;menu=null},{name->
        val existing=rename;naming=false;menu=null
        update {list,mappings->
            if(existing!=null) list.map {if(it.id==existing.id)it.copy(name=name)else it} to mappings
            else {
                val id=UUID.randomUUID().toString()
                (list+EqOutputProfile(id,name,snapshot.bands,snapshot.bass,snapshot.space,snapshot.preamp)) to (mappings+(saveOutput.encoded to id))
            }
        }
    })
    editing?.let {original->EqProfileEditor(original,{editing=null},{updated->
        editing=null;update {list,mappings->list.map {if(it.id==original.id)updated else it} to mappings}
    })}
    deleting?.let {profile->AkiraConfirmationDialog(onDismissRequest={deleting=null},
        title={Text(localizedText("Delete profile?","¿Eliminar perfil?"))},text={Text(profile.name)},
        confirmButton={TextButton({deleting=null;update {list,mappings->
            list.filterNot {it.id==profile.id} to mappings.filterValues {it!=profile.id}
        }}) {Text(strings.remove,color=MaterialTheme.colorScheme.error)}},
        dismissButton={TextButton({deleting=null}) {Text(strings.cancel)}})}
}

@Composable private fun ProfileChoiceRow(title:String,detail:String,selected:Boolean,enabled:Boolean,
    onClick:()->Unit,trailing:(@Composable ()->Unit)?=null) {
    Surface(shape=androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        color=if(selected)audioAccent().copy(alpha=.1f) else MaterialTheme.colorScheme.onSurface.copy(alpha=.04f)) {
        Row(Modifier.fillMaxWidth().selectable(selected,enabled=enabled,role=Role.RadioButton,onClick=onClick)
            .heightIn(min=72.dp).padding(start=16.dp,end=4.dp,top=10.dp,bottom=10.dp),
            horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(title,style=MaterialTheme.typography.titleMedium,maxLines=2,overflow=TextOverflow.Ellipsis)
                Text(detail,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if(selected)Icon(AkiraIcons.Check,null,Modifier.size(20.dp),tint=audioAccent())
            trailing?.invoke()
        }
    }
}

@Composable internal fun EqProfileEditor(original:EqOutputProfile,onDismiss:()->Unit,onSave:(EqOutputProfile)->Unit) {
    val strings=LocalStrings.current
    var profile by remember(original.id) {mutableStateOf(original)}
    AkiraConfirmationDialog(onDismissRequest=onDismiss,title={Text(profile.name)},text={
        Column(Modifier.fillMaxWidth().heightIn(max=420.dp)) {
            EqResponseGraph(profile.bands,MaterialTheme.colorScheme.onSurface,Modifier.fillMaxWidth().height(104.dp))
            Column(Modifier.weight(1f,fill=false).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                profile.bands.forEachIndexed {index,gain->
                    AudioValueControl(strings.eqBandLabel(index),gain,-12f..12f,.5f,
                        {strings.ui(com.akira.app.core.i18n.AkiraUiText.GainFormat,it)},onCommit={value->
                        profile=profile.copy(bands=profile.bands.toMutableList().also {it[index]=value})
                    })
                }
                AudioValueControl(strings.ui(com.akira.app.core.i18n.AkiraUiText.Preamp),20f*log10(profile.preamp),-12f..0f,.5f,
                    {strings.ui(com.akira.app.core.i18n.AkiraUiText.GainFormat,it)},{profile=profile.copy(preamp=10f.pow(it/20f))})
                AudioValueControl(strings.library.bass,profile.bass,0f..1f,.01f,{"${(it*100).toInt()}%"},
                    {profile=profile.copy(bass=it)},inputScale=100f)
                AudioValueControl(strings.library.space,profile.space,0f..1f,.01f,{"${(it*100).toInt()}%"},
                    {profile=profile.copy(space=it)},inputScale=100f)
            }
        }
    },confirmButton={TextButton({onSave(profile)}) {Text(strings.library.save)}},
        dismissButton={TextButton(onDismiss) {Text(strings.cancel)}})
}
