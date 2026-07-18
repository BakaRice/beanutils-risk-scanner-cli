# BeanUtils Risk Scanner CLI

一个基于 Java 17 的独立命令行工具，用于编译并扫描 Maven 单模块或多模块项目中的 Spring `BeanUtils.copyProperties` 调用，识别从 Spring 5.0.7 升级到 5.3.1 后因泛型检查变严格而产生的潜在复制变化。

扫描器只读取目标项目源码和本地 Maven 仓库，不修改业务代码，也不要求目标项目先升级到 JDK 17 或 Spring 5.3.1。

## 核心能力

- 跨 Maven module 统一索引源码、编译后的 Bean 和调用关系
- 识别直接调用、静态导入、Lambda、方法引用和常见项目内包装方法
- 支持 `ignoreProperties`、数组常量和 `editable` 重载
- 分析 List、Set、Map、Optional、PageInfo、自定义容器及嵌套泛型
- 覆盖泛型数组、raw type、通配符、泛型继承和 getter/setter 组合
- 输出 Source/Target JavaBean 属性并集，区分同名映射、同名不可复制和单边独有属性
- 父类继承属性会参与映射，并显示 getter/setter 的实际声明类
- 从 `target/classes` 读取 Lombok/其他注解处理器生成的 getter/setter
- 扫描日志逐类打印全部属性、继承层次、声明类和证据来源
- 输出完全离线的多页 HTML 审计文档和可选 JSON 数据
- 报告第一列为结论，并展示位置、Source 类型、Target 类型及逐属性分析

## 环境要求

- JDK 17
- 首次执行 Maven Wrapper 时可以访问 Maven Central
- 待扫描项目的依赖最好已存在于本地 Maven 仓库

CLI 会先执行目标项目的 Maven `compile`；指定 `--include-tests` 时执行 `test-compile`。它优先使用项目自己的 `mvnw` / `mvnw.cmd`，否则使用环境中的 `mvn`。Maven 会按项目自身配置处理 Lombok 等注解处理器和依赖下载。

编译成功后，属性解析优先读取所有 module 的 `target/classes`（以及可选的 `target/test-classes`），源码仍负责定位调用位置和调用链。编译失败不会终止扫描：工具会打印 `COMPILE-ERROR ... fallback=source`，忽略可能陈旧的 class 并回退源码推断。

## 构建

仓库自带 Maven 3.9.9 Wrapper：

```bash
./mvnw clean verify
```

生成的可执行 fat JAR：

```text
target/beanutils-risk-scanner-cli.jar
```

## 使用

```bash
java -jar target/beanutils-risk-scanner-cli.jar \
  --project /path/to/your-maven-project \
  --output /path/to/beanutils-risk-report.html \
  --json-output /path/to/beanutils-risk-report.json
```

只生成默认 HTML 报告：

```bash
java -jar target/beanutils-risk-scanner-cli.jar \
  --project /path/to/your-maven-project
```

默认输出为当前目录下的 `beanutils-risk-report.html`。

## CLI 参数

| 参数 | 说明 |
|---|---|
| `--project <path>` | Maven 根项目，必须包含 `pom.xml`，必填 |
| `--output <path>` | HTML 报告索引页；同时生成 `<文件名>-details/` 详情目录。默认 `beanutils-risk-report.html` |
| `--json-output <path>` | 可选的结构化 JSON 报告 |
| `--local-repository <path>` | Maven 本地仓库；优先级最高 |
| `--settings <path>` | 从 Maven `settings.xml` 读取 `localRepository` |
| `--include-tests` | 同时扫描 `src/test/java` |
| `--open` | 生成后尝试用系统浏览器打开报告 |
| `--help` | 显示帮助 |
| `--version` | 显示版本 |

## 编译和属性解析日志

CLI 默认把完整过程打印到标准输出：

- `COMPILE` / `MAVEN` / `COMPILE-END`：Maven 编译命令、输出和结果。
- `BEAN`：Bean 完整类型、继承层次以及证据来源。`compiled-class` 表示来自真实 class，`source-fallback` 表示源码回退。
- `PROPERTY`：每个属性的 getter 类型、setter 类型和各自声明类，缺失值用 `-`。
- `BEAN-END`：该 Bean 的完整属性数量，包括明确的零属性结果。
- `BEAN-ERROR`：方法引用、类型变量、null 或无法解析类型的具体原因。

同一个具体类型在一次扫描中只打印一次；父类泛型使用具体实参分别打印。需要留档时可把标准输出重定向到日志文件。Java API 的 `scan(ScanRequest)` 保持静默，接收 `Consumer<String>` 的重载用于获取相同追踪日志。

## 结论说明

- `RISK`：擦除后的原始类型可赋值，但 Spring 5.3 的泛型感知检查会拒绝，属于升级行为变化。
- `SAFE`：泛型兼容，或原始类型本来就不兼容而在新旧版本中都会跳过。
- `IGNORED`：潜在差异字段被 `ignoreProperties` 或 `editable` 显式排除。
- `REVIEW`：raw type、类型变量、方法引用、反射、包装方法、高阶函数、null 参数或缺失类型信息，需要人工复核。

## HTML 报告

HTML 报告是完全离线的多页文档，不依赖 CDN、Web 服务或外部 JavaScript。输出 `beanutils-risk-report.html` 时，会在旁边同时生成 `beanutils-risk-report-details/` 详情目录；移动或压缩报告时需要保留两者的相对位置。页面支持：

- 按 `RISK`、`REVIEW`、`IGNORED`、`SAFE` 筛选
- 按位置、类型和代码全文搜索
- 点击任意主表行进入该调用自己的独立详情页
- 分别查看 Source Bean 和 Target Bean 的全部已解析属性，不因映射失败或没有同名属性而隐藏
- 查看两个 Bean 的完整属性并集以及每个属性的 Source/Target 类型
- 区分“同名已映射”“同名但不可复制”“Source 独有”“Target 独有”
- 查看继承属性的实际声明类
- 查看旧版原始类型判断、5.3 泛型判断和具体原因
- 查看项目内包装方法的已知调用链

主表列顺序为：结论、代码位置、Source 类型、Target 类型、属性概览、详情入口。

## 已覆盖调用场景

- 直接调用和静态导入
- for、增强 for、Iterator、嵌套循环和 Map 遍历
- Stream map/forEach/peek、Optional.map 和 BiConsumer
- Lambda、方法引用和高阶函数
- 泛型包装方法、Supplier 构造器引用和 Class 字面量
- `ignoreProperties` 字面量、varargs、数组常量
- `editable` 重载
- 跨 module Bean copy
- Lombok/注解处理器生成访问器、父类继承访问器和父类泛型具体化

## 静态分析边界

动态反射目标、只在运行期生成且未写入 Maven 编译输出的类、跨进程调用、任意复杂高阶函数和缺失依赖中的类型无法始终被静态证明。这些情况不会被标记为安全，而会以 `REVIEW` 或诊断信息展示。

## 上传到 GitHub

本仓库已经初始化为独立 Git 仓库。创建空的 GitHub 仓库后执行：

```bash
git remote add origin git@github.com:YOUR_ACCOUNT/beanutils-risk-scanner-cli.git
git push -u origin main
```
