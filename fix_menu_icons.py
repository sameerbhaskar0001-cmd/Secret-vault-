import re

file_path = "app/src/main/java/com/example/SecretBrowserViews.kt"
with open(file_path, "r") as f:
    content = f.read()

target1 = """                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(Icons.Default.MoreVert, "More Options", tint = TextPrimary, modifier = Modifier.size(19.dp))
                        }"""

replacement1 = """                        IconButton(
                            onClick = { showMenu = !showMenu },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = if (showMenu) Icons.Default.Close else Icons.Default.MoreVert, 
                                contentDescription = if (showMenu) "Close Menu" else "More Options", 
                                tint = TextPrimary, 
                                modifier = Modifier.size(19.dp)
                            )
                        }"""

target2 = """                        // Panic Mode Presentation
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 2.dp, bottom = 4.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = DangerColor.copy(alpha = 0.06f)),
                            border = BorderStroke(1.dp, DangerColor.copy(alpha = 0.25f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = "Panic Mode",
                                        tint = DangerColor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Panic Mode",
                                        color = TextPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Close Secret Browser and clear sensitive browsing session",
                                    color = TextSecondary,
                                    fontSize = 11.5.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Button(
                                    onClick = {
                                        showMenu = false
                                        try {
                                            SecretBrowserSecureDelete.cleanTemporaryUploadsDirectory(context, secure = true)
                                            SecretBrowserSecureDelete.cleanStaleTemporaryRemnants(context, secure = true)
                                        } catch (e: Exception) {
                                            android.util.Log.e("SecureDelete", "Panic cleanup failed", e)
                                        }
                                        if (clearHistoryOnExit) {
                                            viewModel.clearBrowserHistory()
                                        }
                                        clearAllBrowsingData(context, tabs)
                                        geckoViews.values.forEach { try { (it.parent as? android.view.ViewGroup)?.removeView(it); it.releaseSession() } catch (e: Exception) {} }
                                        geckoViews.clear()
                                        geckoSessions.clear()
                                        onPanic()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = DangerColor),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth().height(40.dp)
                                ) {
                                    Text(
                                        text = "Activate Panic Mode",
                                        color = Color.White,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }"""

replacement2 = """                        // Panic Mode Presentation
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(DangerColor, CircleShape)
                                    .clickable {
                                        showMenu = false
                                        try {
                                            SecretBrowserSecureDelete.cleanTemporaryUploadsDirectory(context, secure = true)
                                            SecretBrowserSecureDelete.cleanStaleTemporaryRemnants(context, secure = true)
                                        } catch (e: Exception) {
                                            android.util.Log.e("SecureDelete", "Panic cleanup failed", e)
                                        }
                                        if (clearHistoryOnExit) {
                                            viewModel.clearBrowserHistory()
                                        }
                                        clearAllBrowsingData(context, tabs)
                                        geckoViews.values.forEach { try { (it.parent as? android.view.ViewGroup)?.removeView(it); it.releaseSession() } catch (e: Exception) {} }
                                        geckoViews.clear()
                                        geckoSessions.clear()
                                        onPanic()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = "Panic Mode", tint = Color.White, modifier = Modifier.size(24.dp))
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Panic Mode", color = DangerColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }"""

if target1 in content:
    content = content.replace(target1, replacement1)
else:
    print("target1 not found")

if target2 in content:
    content = content.replace(target2, replacement2)
else:
    print("target2 not found")

with open(file_path, "w") as f:
    f.write(content)

print("Done")
