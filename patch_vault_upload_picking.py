import re

with open("app/src/main/java/com/example/VaultBrowserIntegration.kt", "r") as f:
    content = f.read()

target1 = """    val multipleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->"""
replace1 = """    val multipleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        viewModel.isPickingFile = false"""

target2 = """    val singleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->"""
replace2 = """    val singleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        viewModel.isPickingFile = false"""

target3 = """    val takePhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->"""
replace3 = """    val takePhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        viewModel.isPickingFile = false"""
        
target4 = """    val recordVideoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CaptureVideo()
    ) { success ->"""
replace4 = """    val recordVideoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CaptureVideo()
    ) { success ->
        viewModel.isPickingFile = false"""

# Now add isPickingFile = true when clicking
target5 = """                            if (activeUpload.isMultiple) {
                                multipleLauncher.launch(mimeFilter)
                            } else {
                                singleLauncher.launch(mimeFilter)
                            }"""
replace5 = """                            viewModel.isPickingFile = true
                            if (activeUpload.isMultiple) {
                                multipleLauncher.launch(mimeFilter)
                            } else {
                                singleLauncher.launch(mimeFilter)
                            }"""

target6 = """                                if (isImageAllowed) {
                                    takePhotoLauncher.launch(tempPhotoUri)
                                } else {
                                    recordVideoLauncher.launch(tempVideoUri)
                                }"""
replace6 = """                                viewModel.isPickingFile = true
                                if (isImageAllowed) {
                                    takePhotoLauncher.launch(tempPhotoUri)
                                } else {
                                    recordVideoLauncher.launch(tempVideoUri)
                                }"""

content = content.replace(target1, replace1)
content = content.replace(target2, replace2)
content = content.replace(target3, replace3)
content = content.replace(target4, replace4)
content = content.replace(target5, replace5)
content = content.replace(target6, replace6)

with open("app/src/main/java/com/example/VaultBrowserIntegration.kt", "w") as f:
    f.write(content)
print("Patched VaultBrowserIntegration with isPickingFile")
