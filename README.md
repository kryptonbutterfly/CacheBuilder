# CacheASM

ByteCode manipulator used to decorate methods using @Cache annotations.

## Install

create the following directories in your eclipse installation directory:
```shell
builder/buildJars
builder/buildSh
```
```shell
copy CacheASM.jar to builder/buildJars
copy CacheASM.sh to builder/buildSh
```

##Project Setup

- Navigate to: Project->Properties->Builders
- click New
- select "Program"
- press OK
- name your Builder CacheASM
- in Location enter the path to CacheASM.sh
- in Working Directory enter the path of your projects bin folder.
- open the "Build Options" Tab
- select the CheckBoxes:
    - Allocate Console
    - After a "Clean"
    - During manual builds
    - During auto builds
- click "Apply"
- click "OK"
- click "Apply and Close"