# CacheASM

ByteCode manipulator used to decorate methods using **[@Cache](https://github.com/tinycodecrank/tinyCache)** annotations.

## Download

java version | library version | Download
:----------: | :-------------: | :-------
18+          | 1.0.0           | [CacheASM.zip](https://github.com/tinycodecrank/CacheBuilder/releases/download/v1.0.0/CacheASM.zip)

## Install

extract **CacheASM.zip** in your <span style="color:#00aaee">**eclipse install directory**</span>.

![](md/icons/eclipse-install-directory.png)

## Project Setup

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

## Dependencies
* [**asm-9.2.jar**](https://repo1.maven.org/maven2/org/ow2/asm/asm/9.2/asm-9.2.jar)
* [**asm-tree-9.2.jar**](https://repo1.maven.org/maven2/org/ow2/asm/asm-tree/9.2/asm-tree-9.2.jar)
* eclipse jar-in-jar-loader.zip
* [**tinySystemUtils.jar**](https://github.com/tinycodecrank/tinySystemUtils/releases/download/1.0.0/tinySystemUtils.jar)
* [**tinyConcurrent.jar**](https://github.com/tinycodecrank/tinyConcurrent/releases/download/v1.0.0/tinyConcurrent.jar)
* [**tinyCache.jar**](https://github.com/tinycodecrank/tinyCache/releases/download/v1.0.0/tinyCache.jar)
* [**Monads.jar**](https://github.com/tinycodecrank/tinyMonads/releases/download/v1.0.0/Monads.jar)
* [**tinyIterators.jar**](https://github.com/tinycodecrank/tinyIterators/releases/download/v1.0.0/tinyIterators.jar)
* [**BetterFunctionals.jar**](https://github.com/tinycodecrank/betterFunctionals/releases/download/v1.0.0/BetterFunctionals.jar)
* [**MathUtils.jar**](https://github.com/tinycodecrank/mathUtils/releases/download/v1.0.0/MathUtils.jar)
* [**BoundedValues.jar**](https://github.com/tinycodecrank/BoundedValues/releases/download/v1.0.0/BoundedValues.jar)
* [**tinyCollections.jar**](https://github.com/tinycodecrank/tinyCollections/releases/download/v1.0.0/tinyCollections.jar)
