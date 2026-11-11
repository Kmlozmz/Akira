package com.akira.app.core.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.akira.app.core.design.system.AkiraPanelSpec

/** Selection is expressed by a check and a quiet fill in either surface style. */
@Composable
fun AkiraChoiceRow(icon:ImageVector,title:String,description:String,selected:Boolean,
    onClick:()->Unit,modifier:Modifier=Modifier) {
    val scheme=MaterialTheme.colorScheme
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(AkiraPanelSpec.choiceRadius.dp))
        .background(if(selected)scheme.onSurface.copy(alpha=.065f)else Color.Transparent)
        .clickable(role=Role.RadioButton,onClick=onClick).semantics{this.selected=selected}
        .padding(horizontal=14.dp,vertical=14.dp),verticalAlignment=Alignment.CenterVertically){
        Box(Modifier.size(38.dp).background(scheme.onSurface.copy(alpha=.055f),RoundedCornerShape(12.dp)),
            contentAlignment=Alignment.Center){Icon(icon,null,Modifier.size(22.dp),tint=scheme.onSurface)}
        Column(Modifier.weight(1f).padding(horizontal=12.dp)){
            Text(title,style=MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(3.dp))
            Text(description,style=MaterialTheme.typography.bodySmall,color=scheme.onSurfaceVariant)
        }
        Box(Modifier.size(22.dp),contentAlignment=Alignment.Center){
            if(selected)Icon(Icons.Rounded.Check,null,Modifier.size(20.dp),tint=scheme.primary)
        }
    }
}
