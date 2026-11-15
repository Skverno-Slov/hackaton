package com.nepalindrom.fielddispatch.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nepalindrom.fielddispatch.ui.components.DateSelectionDialog
import com.nepalindrom.fielddispatch.ui.components.ServerConfigDialog
import com.nepalindrom.fielddispatch.ui.engineer.EngineerScreen
import com.nepalindrom.fielddispatch.ui.engineer.EngineerViewModel
import com.nepalindrom.fielddispatch.ui.theme.RostelecomPurple

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    mainViewModel: MainViewModel = viewModel(),
    engineerViewModel: EngineerViewModel = viewModel()
) {
    val currentDate by mainViewModel.currentDate.collectAsState()
    val serverUrl by mainViewModel.serverUrl.collectAsState()
    val staffProfile by engineerViewModel.staffProfile.collectAsState()
    val selectedStaffId by engineerViewModel.selectedStaffId.collectAsState()
    val isAuthenticated by engineerViewModel.isAuthenticated.collectAsState()
    val isLoggingIn by engineerViewModel.isLoggingIn.collectAsState()
    val loginError by engineerViewModel.loginError.collectAsState()

    var showServerConfig by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        mainViewModel.syncServerActiveDate { syncedDate ->
            engineerViewModel.loadPlan(syncedDate)
        }
    }

    val isLoggedIn = (staffProfile != null && selectedStaffId.isNotBlank()) || isAuthenticated

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isLoggedIn) Icons.Default.Person else Icons.Default.Build,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            if (isLoggedIn && staffProfile != null) {
                                Text(
                                    text = "${staffProfile!!.displayName} (${staffProfile!!.staffId})",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    color = Color.White
                                )
                                Text(
                                    text = staffProfile!!.serviceCenterName,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    color = Color.White.copy(alpha = 0.85f)
                                )
                            } else {
                                Text(
                                    text = "FieldDispatch",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    maxLines = 1,
                                    color = Color.White
                                )
                                Text(
                                    text = "Ростелеком (Екатеринбург)",
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    color = Color.White.copy(alpha = 0.85f)
                                )
                            }
                        }
                    }
                },
                actions = {
                    // Clickable Date Indicator Pill
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.White.copy(alpha = 0.18f),
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .clickable { showDatePicker = true }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.DateRange, contentDescription = "Выбор даты", tint = Color.White, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(currentDate, fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Refresh Button
                    IconButton(onClick = {
                        engineerViewModel.loadReference()
                        engineerViewModel.loadPlan(currentDate)
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Обновить", tint = Color.White)
                    }

                    // Server Settings Button
                    IconButton(onClick = { showServerConfig = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Настройки", tint = Color.White)
                    }

                    // Logout button (when logged in)
                    if (isLoggedIn) {
                        IconButton(onClick = {
                            engineerViewModel.logout()
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Выйти из аккаунта", tint = Color.White)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = RostelecomPurple)
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (isLoggedIn) {
                EngineerScreen(
                    currentDate = currentDate,
                    viewModel = engineerViewModel,
                    onSelectDateClick = { showDatePicker = true }
                )
            } else {
                // Login screen
                StaffLoginScreen(
                    loginError = loginError,
                    isLoggingIn = isLoggingIn,
                    onLogin = { login, password ->
                        engineerViewModel.login(login, password, currentDate)
                    },
                    onOpenSettings = { showServerConfig = true }
                )
            }
        }
    }

    if (showDatePicker) {
        DateSelectionDialog(
            currentDate = currentDate,
            onDismiss = { showDatePicker = false },
            onDateSelected = { newDate ->
                mainViewModel.setDate(newDate)
                engineerViewModel.loadPlan(newDate)
                showDatePicker = false
            }
        )
    }

    if (showServerConfig) {
        ServerConfigDialog(
            currentUrl = serverUrl,
            onDismiss = { showServerConfig = false },
            onSave = { newUrl ->
                mainViewModel.updateServerUrl(newUrl)
                engineerViewModel.loadReference()
                engineerViewModel.loadPlan(currentDate)
                showServerConfig = false
            }
        )
    }
}

@Composable
private fun StaffLoginScreen(
    loginError: String?,
    isLoggingIn: Boolean,
    onLogin: (String, String) -> Unit,
    onOpenSettings: () -> Unit
) {
    var loginInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 4.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(RostelecomPurple.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = RostelecomPurple,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Вход в систему",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Авторизуйтесь для доступа к маршруту выездного сотрудника",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

                OutlinedTextField(
                    value = loginInput,
                    onValueChange = { loginInput = it },
                    label = { Text("Логин") },
                    placeholder = { Text("ST-001") },
                    leadingIcon = {
                        Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(20.dp))
                    },
                    singleLine = true,
                    enabled = !isLoggingIn,
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Next
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = passwordInput,
                    onValueChange = { passwordInput = it },
                    label = { Text("Пароль") },
                    placeholder = { Text("••••••••") },
                    leadingIcon = {
                        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(20.dp))
                    },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (passwordVisible) "Скрыть пароль" else "Показать пароль",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    enabled = !isLoggingIn,
                    isError = loginError != null,
                    supportingText = if (loginError != null) {
                        { Text(loginError, color = MaterialTheme.colorScheme.error) }
                    } else null,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (loginInput.isNotBlank() && passwordInput.isNotBlank() && !isLoggingIn) {
                                onLogin(loginInput, passwordInput)
                            }
                        }
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { onLogin(loginInput, passwordInput) },
                    colors = ButtonDefaults.buttonColors(containerColor = RostelecomPurple),
                    enabled = loginInput.isNotBlank() && passwordInput.isNotBlank() && !isLoggingIn,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isLoggingIn) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Войти", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(onClick = onOpenSettings) {
                    Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Настройки подключения", fontSize = 12.sp)
                }
            }
        }
    }
}
