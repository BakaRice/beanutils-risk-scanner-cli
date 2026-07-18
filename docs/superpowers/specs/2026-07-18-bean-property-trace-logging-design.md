# Bean 属性解析追踪日志设计

## 目标

CLI 先编译 Maven 项目，再把每个实际参与 BeanUtils 风险分析的 Source/Target Bean 属性模型打印到标准输出，使用户能在最终 HTML 出现空缺或 `REVIEW` 时回看中间解析证据。属性优先来自注解处理器执行后的 `.class`，源码只作为失败回退。

## 日志范围

- 直接 `BeanUtils.copyProperties` 调用和项目内包装调用共用同一个追踪器。
- 相同的具体 Bean 类型在一次扫描中只打印一次；泛型实参不同的具体类型分别打印。
- 只打印参与 BeanUtils 判定的 Bean 类型，不打印与 Bean copy 无关的全部项目类。
- 父类提供的 getter/setter 会显示父类的实际声明类型。
- 合成的 `class` 属性不作为业务属性打印。
- CLI 使用 Maven Wrapper（缺失时使用 `mvn`）执行 `compile` / `test-compile`；失败时不中断扫描，但禁用编译产物以避免读取陈旧 class。
- 每个 Bean 显式标记 `compiled-class` 或 `source-fallback`，方便判断 Lombok 等生成访问器是否已经生效。

## 日志格式

```text
[BeanUtilsScanner][COMPILE-END] status=success
[BeanUtilsScanner][BEAN] type=com.example.Source evidence=compiled-class hierarchy=com.example.Source -> com.example.Base
[BeanUtilsScanner][PROPERTY] bean=com.example.Source name=orders readType=java.util.List<OrderDO> writeType=java.util.List<OrderDO> getterOwner=com.example.Base setterOwner=com.example.Base
[BeanUtilsScanner][BEAN-END] type=com.example.Source properties=1
```

缺少 getter、setter、类型或声明类时用 `-` 明确占位。类型声明无法解析时输出：

```text
[BeanUtilsScanner][BEAN-ERROR] type=example.Missing reason=missing-type-declaration
```

## 架构

- `BeanPropertyTraceLogger` 负责去重和稳定文本格式，接收一个 `Consumer<String>`，便于 CLI 输出与测试收集。
- `DirectCopyAnalyzer` 和 `WrapperCallAnalyzer` 接收同一个日志器并传给 `BeanPropertyResolver`。
- `BeanUtilsRiskScanner.scan(request)` 保持静默，新增接收日志行消费者的重载。
- `ScanCommand` 使用 `System.out::println` 启用详细追踪。
- `MavenProjectCompiler` 在扫描前构建目标项目，`CompiledProjectClassLoader` 把各 module 输出目录和依赖加入 JavaParser 类型求解器，而且优先级高于源码类型求解器。

## 验证

- 单元测试先验证每个类型和每个属性的日志内容、缺失 setter 占位及类型去重。
- CLI 测试验证默认命令执行会输出 `BEAN` 和 `PROPERTY` 日志。
- 真实 Demo 扫描验证跨 Module、父类继承和泛型具体化后的属性类型出现在日志中。
- 用“源码只有字段、class 含生成访问器”的测试夹具模拟 Lombok，验证报告能够从 class 找到属性和具体泛型。
