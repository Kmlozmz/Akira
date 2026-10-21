package com.akira.app.feature.settings

import android.media.AudioDeviceInfo
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.akira.app.core.audio.*
import com.akira.app.core.datastore.*
import com.akira.app.core.i18n.LocalStrings
import com.akira.app.core.design.icon.AkiraIcons
import com.akira.app.core.design.components.*
import com.akira.app.core.design.components.localizedText
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import java.util.UUID
import org.json.JSONObject

/** Profiles own their editor/menu state; closing an editor always returns to this list. */
@Composable
internal fun OutputProfilesSheet(onDismiss: () -> Unit) {
    val context=LocalContext.current
    val repo=remember {UserPreferencesRepository(context)}
    val prefs by repo.userPreferences.collectAsStateWithLifecycle(UserPreferences())
    val output by OutputRoutes.current.collectAsStateWithLifecycle()
    val profiles=remember(prefs.eqProfilesJson) {OutputProfiles.decode(prefs.eqProfilesJson)}
    val assignments=remember(prefs.eqAssignmentsJson) {OutputProfiles.assignments(prefs.eqAssignmentsJson)}
    val strings=LocalStrings.current
    val scope=rememberCoroutineScope()
    var naming by remember {mutableStateOf(false)}
    var target by remember {mutableStateOf<EqOutputProfile?>(null)}
    var editing by remember {mutableStateOf<EqOutputProfile?>(null)}
    var menu by remember {mutableStateOf<String?>(null)}
    var saving by remember {mutableStateOf(false)}
    val assigned=profiles.firstOrNull {it.id==assignments[output.encoded]}
    val outputName=if(output.type==AudioDeviceInfo.TYPE_BUILTIN_SPEAKER)strings.deviceSpeaker else output.productName.ifBlank {strings.library.output}
    fun update(edit: (List<EqOutputProfile>,Map<String,String>)->Pair<List<EqOutputProfile>,Map<String,String>>) {
        if(saving)return
        saving=true
        scope.launch {
            try {
                val latest=repo.userPreferences.first()
                val (values,mappings)=edit(OutputProfiles.decode(latest.eqProfilesJson),OutputProfiles.assignments(latest.eqAssignmentsJson))
                repo.setOutputProfiles(OutputProfiles.encode(values),JSONObject(mappings).toString())
            } catch(cancel: kotlinx.coroutines.CancellationException) {throw cancel}
            catch(_: Exception) {AkiraNotices.show(strings.library.error)}
            finally {saving=false}
        }
    }
    AkiraModalSheet(onDismissRequest={if(!saving)onDismiss()},title=strings.library.profiles) {
        Column(Modifier.fillMaxWidth().padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Text(strings.library.profiles,style=MaterialTheme.typography.titleLarge)
            Text(localizedText("Save a different equalizer for your speaker or headphones. An assigned profile is applied automatically whenever that output connects.",
                "Guarda una ecualización para el altavoz y otra para tus auriculares. El perfil asignado se aplica automáticamente al conectar esa salida."),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Text(localizedText("Current output: $outputName","Salida actual: $outputName"),style=MaterialTheme.typography.titleSmall)
            Text(localizedText("Using: ${assigned?.name ?: strings.library.globalProfile}","En uso: ${assigned?.name ?: strings.library.globalProfile}"),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            if(!prefs.eqEnabled) {
                Text(localizedText("Equalizer is off. Saved profiles affect sound only when it is enabled.","El ecualizador está apagado. Los perfiles guardados solo cambian el sonido cuando está activado."),style=MaterialTheme.typography.bodySmall)
                OutlinedButton({scope.launch {repo.setEqEnabled(true)}},Modifier.fillMaxWidth(),enabled=!saving) {Text(localizedText("Enable equalizer","Activar ecualizador"))}
            }
            Button({menu=null;target=null;naming=true},Modifier.fillMaxWidth(),enabled=!saving) {Text(localizedText("Save current equalizer","Guardar ecualización actual"))}
            TextButton({update {list,mappings->list to (mappings-output.encoded)}},Modifier.fillMaxWidth(),enabled=!saving) {
                Text(strings.library.globalProfile,Modifier.weight(1f))
                if(assigned==null)Icon(AkiraIcons.Check,null)
            }
            if(profiles.isEmpty())Text(localizedText("No saved profiles yet. Save your current sound, then assign it to this output.","Aún no hay perfiles. Guarda tu sonido actual y después asígnalo a esta salida."),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            else LazyColumn(Modifier.fillMaxWidth().heightIn(max=280.dp),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                itemsIndexed(profiles,key={_,profile->profile.id}) {index,profile->
                    Surface(shape=settingsSegmentShape(index,profiles.size),color=MaterialTheme.colorScheme.surfaceContainerHigh) {
                        Row(Modifier.fillMaxWidth().clickable(enabled=!saving) {menu=null;editing=profile}.padding(start=16.dp,end=4.dp,top=8.dp,bottom=8.dp),verticalAlignment=Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(profile.name,style=MaterialTheme.typography.titleSmall)
                                Text(if(assigned?.id==profile.id)localizedText("Assigned to $outputName","Asignado a $outputName") else localizedText("Tap to edit equalizer","Toca para editar la ecualización"),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if(assigned?.id==profile.id)Icon(AkiraIcons.Check,null,Modifier.size(18.dp))
                            AkiraIconButton(AkiraIcons.MoreHoriz,strings.options,{if(!saving)menu=profile.id})
                        }
                    }
                }
            }
        }
    }
    // A single menu outside the lazy row survives list mutations safely. Drop
    // it before opening an editor, so closing Rename cannot resurrect it.
    profiles.firstOrNull {it.id==menu}?.let {profile ->
        AkiraContextMenu(onDismiss={menu=null},groups=listOf(listOf(
            AkiraContextAction(AkiraIcons.Edit,strings.library.rename,opensPresentation=true) {menu=null;target=profile;naming=true},
            AkiraContextAction(AkiraIcons.Queue,strings.library.duplicate) {menu=null;update {list,mappings->
                val latest=list.firstOrNull{it.id==profile.id} ?: profile
                (list+latest.copy(id=UUID.randomUUID().toString(),name="${latest.name} (${strings.library.duplicate})")) to mappings
            }},
            AkiraContextAction(AkiraIcons.Airplay,strings.library.assignOutput,selected=assigned?.id==profile.id) {menu=null;update {list,mappings->list to (mappings+(output.encoded to profile.id))}}
        ),listOf(AkiraContextAction(AkiraIcons.Trash,strings.remove,destructive=true) {menu=null;update {list,mappings->list.filterNot{it.id==profile.id} to mappings.filterValues{it!=profile.id}}})))
    }
    if(naming)AkiraNameDialog(target?.name.orEmpty(),{naming=false;menu=null},{name ->
        val existing=target
        val snapshot=assigned
        naming=false;menu=null
        update {list,mappings ->
            (if(existing==null)list+EqOutputProfile(UUID.randomUUID().toString(),name,snapshot?.bands ?: prefs.eqBandGains,snapshot?.bass ?: prefs.bassBoost,snapshot?.space ?: prefs.virtualizer)
            else list.map {if(it.id==existing.id)it.copy(name=name) else it}) to mappings
        }
    })
    editing?.let {original ->EqProfileEditor(original,{editing=null;menu=null},{updated ->
        editing=null;menu=null;update {list,mappings->list.map {if(it.id==original.id)updated else it} to mappings}
    })}
}

@Composable
internal fun EqProfileEditor(original: EqOutputProfile, onDismiss: () -> Unit, onSave: (EqOutputProfile) -> Unit) {
    val strings = LocalStrings.current
    var profile by remember(original.id) { mutableStateOf(original) }
    com.akira.app.core.design.components.AkiraConfirmationDialog(onDismissRequest = onDismiss,
        title = { Text(profile.name) }, text = {
            Column(Modifier.verticalScroll(rememberScrollState(), flingBehavior = com.akira.app.core.design.motion.rememberAkiraBouncingFling())) {
                profile.bands.indices.forEach { band ->
                    Text(strings.eqBandLabel(band))
                    Slider(profile.bands[band], { gain -> profile = profile.copy(bands = profile.bands.toMutableList().also { it[band] = gain }) }, valueRange = -12f..12f)
                }
                Text(strings.library.bass)
                Slider(profile.bass, { profile = profile.copy(bass = it) })
                Text(strings.library.space)
                Slider(profile.space, { profile = profile.copy(space = it) })
            }
        }, confirmButton = { TextButton(onClick = { onSave(profile) }) { Text(strings.library.save) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(strings.cancel) } })
}
