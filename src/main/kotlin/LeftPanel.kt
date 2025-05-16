import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ResourceTreeView(
    rootIds: List<String>,
    expandedNodes: MutableMap<String, Boolean>,
    selectedResource: String?,
    onNodeClick: (id: String, isLeaf: Boolean) -> Unit,
    modifier: Modifier = Modifier.Companion
) {
    LazyColumn(modifier = modifier.fillMaxSize().padding(start = 8.dp)) {
        rootIds.forEach { nodeId ->
            item {
                ResourceTreeNode(
                    nodeId = nodeId,
                    level = 0,
                    expandedNodes = expandedNodes,
                    selectedResource = selectedResource,
                    onNodeClick = onNodeClick
                )
            }
        }
    }
}


@Composable
fun ResourceTreeNode(
    nodeId: String,
    level: Int,
    expandedNodes: MutableMap<String, Boolean>,
    selectedResource: String?,
    onNodeClick: (id: String, isLeaf: Boolean) -> Unit
) {
    val isLeaf = resourceLeafNodes.contains(nodeId)
    val children = resourceTreeData[nodeId]
    val isExpanded = expandedNodes[nodeId] ?: false
    val isSelected = nodeId == selectedResource

    Row(
        modifier = Modifier.Companion
            .fillMaxWidth()
            .padding(start = (level * 16).dp)
            .clickable { onNodeClick(nodeId, isLeaf) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Companion.CenterVertically
    ) {
        val icon = when {
            !isLeaf && children?.isNotEmpty() == true -> if (isExpanded) ICON_DOWN else ICON_RIGHT
            !isLeaf -> ICON_NF
            else -> ICON_RESOURCE
        }
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.Companion.size(18.dp),
            tint = if (isSelected)
                MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.Companion.width(4.dp))
        Text(
            text = nodeId,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isSelected)
                MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface
        )
    }

    if (!isLeaf && isExpanded && children != null) {
        Column {
            children.sorted().forEach { childId ->
                ResourceTreeNode(
                    nodeId = childId,
                    level = level + 1,
                    expandedNodes = expandedNodes,
                    selectedResource = selectedResource,
                    onNodeClick = onNodeClick
                )
            }
        }
    }
}
