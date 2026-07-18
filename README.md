# BeanUtils Risk Scanner CLI

一个基于 Java 17 的独立命令行工具，用于扫描 Maven 单模块或多模块项目中的 Spring `BeanUtils.copyProperties` 调用，识别从 Spring 5.0.7 升级到 5.3.1 后因泛型检查变严格而产生的潜在复制变化。

扫描器只读取目标项目源码和本地 Maven 仓库，不修改业务代码，也不要求目标项目先升级到 JDK 17 或 Spring 5.3.1。

## 核心能力

- 跨 Maven module 统一索引 Bean 和调用关系
- 识别直接调用、静态导入、Lambda、方法引用和常见项目内包装方法
- 支持 `ignoreProperties`、数组常量和 `editable` 重载
- 分析 List、Set、Map、Optional、PageInfo、自定义容器及嵌套泛型
- 覆盖泛型数组、raw type、通配符、泛型继承和 getter/setter 组合
- 输出 Source/Target JavaBean 属性并集，区分同名映射、同名不可复制和单边独有属性
- 父类继承属性会参与映射，并显示 getter/setter 的实际声明类
- 输出完全离线的 HTML 审计页面和可选 JSON 数据
- 报告第一列为结论，并展示位置、Source 类型、Target 类型及逐属性分析

## 环境要求

- JDK 17
- 首次执行 Maven Wrapper 时可以访问 Maven Central
- 待扫描项目的依赖最好已存在于本地 Maven 仓库

扫描器不会调用 Maven 构建目标项目，也不会自动下载目标项目缺失的依赖。缺失依赖或局部源码无法解析时，工具会保留诊断并尽可能输出其余结果。

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
| `--output <path>` | HTML 报告路径；默认 `beanutils-risk-report.html` |
| `--json-output <path>` | 可选的结构化 JSON 报告 |
| `--local-repository <path>` | Maven 本地仓库；优先级最高 |
| `--settings <path>` | 从 Maven `settings.xml` 读取 `localRepository` |
| `--include-tests` | 同时扫描 `src/test/java` |
| `--open` | 生成后尝试用系统浏览器打开报告 |
| `--help` | 显示帮助 |
| `--version` | 显示版本 |

## 结论说明

- `RISK`：擦除后的原始类型可赋值，但 Spring 5.3 的泛型感知检查会拒绝，属于升级行为变化。
- `SAFE`：泛型兼容，或原始类型本来就不兼容而在新旧版本中都会跳过。
- `IGNORED`：潜在差异字段被 `ignoreProperties` 或 `editable` 显式排除。
- `REVIEW`：raw type、类型变量、方法引用、反射、包装方法、高阶函数、null 参数或缺失类型信息，需要人工复核。

## HTML 报告

HTML 报告是单文件离线页面，不依赖 CDN、Web 服务或外部 JavaScript。页面支持：

- 按 `RISK`、`REVIEW`、`IGNORED`、`SAFE` 筛选
- 按位置、类型和代码全文搜索
- 展开查看 Source/Target Bean 类型
- 查看两个 Bean 的完整属性并集以及每个属性的 Source/Target 类型
- 区分“同名已映射”“同名但不可复制”“Source 独有”“Target 独有”
- 查看继承属性的实际声明类；默认展示全部属性，可切换为仅看差异
- 查看旧版原始类型判断、5.3 泛型判断和具体原因
- 查看项目内包装方法的已知调用链

主表列顺序为：结论、代码位置、Source 类型、Target 类型、详情。

## 已覆盖调用场景

- 直接调用和静态导入
- for、增强 for、Iterator、嵌套循环和 Map 遍历
- Stream map/forEach/peek、Optional.map 和 BiConsumer
- Lambda、方法引用和高阶函数
- 泛型包装方法、Supplier 构造器引用和 Class 字面量
- `ignoreProperties` 字面量、varargs、数组常量
- `editable` 重载
- 跨 module Bean copy

## 静态分析边界

动态反射目标、运行期生成类、跨进程调用、任意复杂高阶函数和缺失依赖中的类型无法始终被静态证明。这些情况不会被标记为安全，而会以 `REVIEW` 或诊断信息展示。

## 上传到 GitHub

本仓库已经初始化为独立 Git 仓库。创建空的 GitHub 仓库后执行：

```bash
git remote add origin git@github.com:YOUR_ACCOUNT/beanutils-risk-scanner-cli.git
git push -u origin main
```
