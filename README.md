# CacheASM

ByteCode manipulator used to decorate methods using **@Cache** annotations.

## Install

extract **CacheASM.zip** in your <span style="color:#00aaee">**eclipse install directory**</span>.

![](md/icons/eclipse-install-directory.png)

##Project Setup

### Import launch config into workspace

- click ![File](md/icons/mnFile.png)
- click ![Import…](md/icons/mnImport.png)
- select ![Run/Debug ➜ Launch Configuration](md/icons/Run_Debug-Launch_Configuration.png)
- click ![Next >](md/icons/Next_>.png)
- click ![Browse](md/icons/Browse.png)
- navigate to <span style="color:#00aaee">**eclipse install directory**</span>**/builder/launch-configs**
  </br>!["eclipse install directory"/builder/launch-configs](md/icons/BrowseLaunchConfig.png)
- click ![Open](md/icons/Open.png)
- highlight **launch-config** select **Cache.launch**
  </br>![](md/icons/ImportSelectLaunchconfig.png)
- click ![Finish](md/icons/Finish.png)

### Setup launch config as project builder
- right click your project
- select ![properties](md/icons/mnProperties.png)
- select ![Builders](md/icons/Builder.png)
- click ![Import…](md/icons/btnImport.png)
- select ![Cache](md/icons/chooseLaunchConfig.png)
- click ![OK](md/icons/btnOK.png)
- click ![Edit…](md/icons/btnEdit.png)
- open the Tab ![Build Options](md/icons/BuildOptions.png)
- select the **CheckBoxes**:
    - ![Allocate Console](md/icons/check_AllocateConsole.png)
    - ![After a "Clean"](md/icons/checkAfterClean.png)
    - ![During manual builds](md/icons/checkManualBuilds.png)
    - ![During auto builds](md/icons/checkAutoBuilds.png)
- click ![Apply](md/icons/btnApply.png)
- click ![OK](md/icons/btnOK.png)
- click ![Apply and Close](md/icons/btnApplyAndClose.png)