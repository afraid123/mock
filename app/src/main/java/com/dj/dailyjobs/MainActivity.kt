package com.dj.dailyjobs

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dj.dailyjobs.data.TaskEntity
import com.dj.dailyjobs.ui.TaskViewModel
import com.dj.dailyjobs.ui.theme.DJTheme
import java.util.Calendar

class MainActivity : ComponentActivity() {
    private val vm: TaskViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
        }
        setContent {
            DJTheme {
                AppScreen(vm)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScreen(vm: TaskViewModel) {
    val ui by vm.uiState.collectAsStateWithLifecycle()
    var showDialog by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add")
            }
        }
    ) { pad ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = ui.search,
                onValueChange = vm::setSearch,
                label = { Text("Search tasks") },
                modifier = Modifier.fillMaxWidth()
            )

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = !ui.showCompleted,
                    onClick = { vm.showCompleted(false) },
                    shape = androidx.compose.material3.SegmentedButtonDefaults.itemShape(0, 2)
                ) { Text("Pending") }
                SegmentedButton(
                    selected = ui.showCompleted,
                    onClick = { vm.showCompleted(true) },
                    shape = androidx.compose.material3.SegmentedButtonDefaults.itemShape(1, 2)
                ) { Text("Completed") }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = { vm.setCategoryFilter(null) }, label = { Text("All") })
                ui.categories.forEach {
                    AssistChip(onClick = { vm.setCategoryFilter(it.id) }, label = { Text(it.name) })
                }
            }

            val list = if (ui.showCompleted) ui.completed else ui.pending
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(list, key = { it.id }) { task ->
                    val dismissState = rememberSwipeToDismissBoxState(
                        positionalThreshold = { it * 0.25f },
                        confirmValueChange = {
                            if (it == SwipeToDismissBoxValue.EndToStart || it == SwipeToDismissBoxValue.StartToEnd) {
                                if (ui.showCompleted) vm.restore(task) else vm.markDone(task)
                                true
                            } else false
                        }
                    )
                    SwipeToDismissBox(state = dismissState, backgroundContent = {}) {
                        TaskRow(task, categoryName = ui.categories.find { c -> c.id == task.categoryId }?.name ?: "")
                    }
                }
            }
        }
    }

    if (showDialog) {
        AddTaskDialog(
            categories = ui.categories,
            onDismiss = { showDialog = false },
            onAddCategory = vm::addCategory,
            onAddTask = { text, catId, reminder, repeat ->
                vm.addTask(text, catId, reminder, repeat)
                showDialog = false
            }
        )
    }
}

@Composable
private fun TaskRow(task: TaskEntity, categoryName: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
    ) {
        Text(
            text = task.text,
            style = MaterialTheme.typography.titleMedium,
            textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null
        )
        Text(text = categoryName, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun AddTaskDialog(
    categories: List<com.dj.dailyjobs.data.CategoryEntity>,
    onDismiss: () -> Unit,
    onAddCategory: (String) -> Unit,
    onAddTask: (String, Long, Long?, Long?) -> Unit
) {
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    var selectedCat by remember { mutableStateOf(categories.firstOrNull()?.id ?: 0L) }
    var newCategory by remember { mutableStateOf("") }
    var reminderMillis by remember { mutableStateOf<Long?>(null) }
    var repeat by remember { mutableStateOf<Long?>(null) }

    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val spoken = it.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty()
        if (spoken.isNotBlank()) text = spoken
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Task") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Task") },
                    trailingIcon = {
                        IconButton(onClick = {
                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
                            }
                            speechLauncher.launch(intent)
                        }) {
                            Icon(Icons.Default.Mic, contentDescription = "Voice input")
                        }
                    }
                )

                OutlinedTextField(
                    value = newCategory,
                    onValueChange = { newCategory = it },
                    label = { Text("New category (optional)") }
                )

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Category:")
                    categories.forEach {
                        AssistChip(onClick = { selectedCat = it.id }, label = { Text(if (selectedCat == it.id) "✓ ${it.name}" else it.name) })
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        val c = Calendar.getInstance()
                        DatePickerDialog(context, { _, y, m, d ->
                            TimePickerDialog(context, { _, hh, mm ->
                                c.set(y, m, d, hh, mm, 0)
                                reminderMillis = c.timeInMillis
                            }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true).show()
                        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
                    }) { Text(if (reminderMillis == null) "Set reminder" else "Reminder set") }
                    TextButton(onClick = { repeat = 24 * 60L }) { Text("Repeat daily") }
                    TextButton(onClick = { repeat = null }) { Text("No repeat") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (newCategory.isNotBlank()) onAddCategory(newCategory)
                if (selectedCat != 0L) onAddTask(text, selectedCat, reminderMillis, repeat)
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
