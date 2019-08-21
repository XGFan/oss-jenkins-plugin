## OSS-Jenkins-Plugin

打包
```
mvn hpi:hpi
```

### 使用方法
![Pipeline](doc/pipeline.png)

![General](doc/general.png)

### 注意事项
会通过MD5判断文件是否重复，跳过已经上传的文件。
大文件可能会有一点点问题。