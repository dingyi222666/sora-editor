/**
 * 这是一个演示 TypeScript 方法重载的计算器类。
 */
class Calculator {
    /**
     * 重载签名 1: 接受两个数字进行相加。
     * @param a 第一个加数。
     * @param b 第二个加数。
     * @returns 两个数字的和。
     */
    add(a: number, b: number): number;

    /**
     * 重载签名 2: 接受两个字符串进行连接。
     * @param a 第一个字符串。
     * @param b 第二个字符串。
     * @returns 两个字符串连接后的结果。
     */
    add(a: string, b: string): string;

    /**
     * 重载签名 3: 接受一个数字数组，计算所有元素的总和。
     * @param arr 包含数字的数组。
     * @returns 数组中所有数字的总和。
     */
    add(arr: number[]): number;

    /**
     * 实现签名: 这是实际执行逻辑的函数体。
     * 注意：它必须涵盖所有重载签名的参数类型和返回类型组合。
     * @param arg1 可能是 number, string, 或 number[]。
     * @param arg2 可能是 number, string, 或 undefined (当只传入一个数组时)。
     * @returns 可能是 number 或 string。
     */
    add(arg1: number | string | number[], arg2?: number | string): number | string {
        // 检查是否是 number[] 类型的数组重载 (重载签名 3)
        if (Array.isArray(arg1) && typeof arg2 === 'undefined') {
            return arg1.reduce((sum, current) => sum + current, 0);
        }

        // 检查是否是 number, number 类型的数字相加重载 (重载签名 1)
        if (typeof arg1 === 'number' && typeof arg2 === 'number') {
            return arg1 + arg2;
        }

        // 检查是否是 string, string 类型的字符串连接重载 (重载签名 2)
        if (typeof arg1 === 'string' && typeof arg2 === 'string') {
            return arg1 + arg2;
        }

        // 如果传入了不符合任何重载签名的参数组合，可以抛出错误或返回默认值
        throw new Error("Invalid parameters provided for the 'add' method.");
    }
}

// --- 演示如何使用重载 ---

const calculator = new Calculator();

// 示例 1: 使用数字相加重载
const sum = calculator.add(5, 10);
console.log(`5 + 10 = ${sum}`); // 输出: 5 + 10 = 15

// 示例 2: 使用字符串连接重载
const combinedString = calculator.add("Hello", " World!");
console.log(`"Hello" + " World!" = ${combinedString}`); // 输出: "Hello World!"

// 示例 3: 使用数字数组求和重载
const arraySum = calculator.add([1, 2, 3, 4]);
console.log(`[1, 2, 3, 4] 的和 = ${arraySum}`); // 输出: [1, 2, 3, 4] 的和 = 10

// 尝试使用未定义的重载 (TypeScript 编译器会在编译阶段报错)
// @ts-ignore: 忽略编译器检查，演示运行时错误
try {
    const invalidCall = calculator.add(5, "error"); // 运行时会进入实现体的 throw new Error
    console.log(`Invalid Call Result: ${invalidCall}`);
} catch (error: any) {
    console.error(`尝试非法调用时捕获到错误: ${error.message}`);
}