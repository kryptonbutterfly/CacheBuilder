# CacheBuilder [![Maven Package](https://github.com/kryptonbutterfly/CacheBuilder/actions/workflows/maven-publish.yml/badge.svg)](https://github.com/kryptonbutterfly/CacheBuilder/actions/workflows/maven-publish.yml)

ByteCode manipulator used to decorate methods using **[@Cache](https://github.com/kryptonbutterfly/tinyCache)** annotations.

## Getting the latest release

```xml
<repository>
  <id>github</id>
  <url>https://maven.pkg.github.com/kryptonbutterfly/maven-repo</url>
</repository>
```
```xml
<dependency>
  <groupId>kryptonbutterfly</groupId>
  <artifactId>cache_builder</artifactId>
  <version>2.0.0</version>
</dependency>
```

## Download

java version | library version | Download
:----------: | :-------------: | :-------
18+          | 2.0.0           | [cache_builder-2.0.0-setup.zip](https://github-registry-files.githubusercontent.com/731108692/2ff3ba00-b6b2-11ee-9145-07a6201d0f00?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Credential=AKIAVCODYLSA53PQK4ZA%2F20240119%2Fus-east-1%2Fs3%2Faws4_request&X-Amz-Date=20240119T090910Z&X-Amz-Expires=300&X-Amz-Signature=38c39e8068ae7b1b73d3190fccb25d32b1a25095c9ab7ab9f2b18002991609d6&X-Amz-SignedHeaders=host&actor_id=0&key_id=0&repo_id=731108692&response-content-disposition=filename%3Dcache_builder-2.0.0-setup.zip&response-content-type=application%2Foctet-stream)</br>[cache_builder-2.0.0-setup.tar.gz](https://github-registry-files.githubusercontent.com/731108692/3124e700-b6b2-11ee-8dc8-e84e0ff6435e?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Credential=AKIAVCODYLSA53PQK4ZA%2F20240119%2Fus-east-1%2Fs3%2Faws4_request&X-Amz-Date=20240119T090910Z&X-Amz-Expires=300&X-Amz-Signature=63d67b14958b5c319ce7fc54b0cfbd748d52d7870e61005a87dfff3734011279&X-Amz-SignedHeaders=host&actor_id=0&key_id=0&repo_id=731108692&response-content-disposition=filename%3Dcache_builder-2.0.0-setup.tar.gz&response-content-type=application%2Foctet-stream)
18+          | 1.1.0           | [CacheBuilder.zip](https://github.com/kryptonbutterfly/CacheBuilder/releases/download/v1.1.0/CacheBuilder.zip)</br>[CacheBuilder.tar.gz](https://github.com/kryptonbutterfly/CacheBuilder/releases/download/v1.1.0/CacheBuilder.tar.gz)
18+          | 1.0.0           | [CacheASM.zip](https://github.com/kryptonbutterfly/CacheBuilder/releases/download/v1.0.0/CacheASM.zip)

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
