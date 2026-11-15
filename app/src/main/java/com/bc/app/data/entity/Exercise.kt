package com.bc.app.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 动作实体类
 * 对应数据库表：exercises
 *
 * subcategory：子分类（器械维度，见 SUBCATEGORY_ORDER）
 *  - 力量部位(胸/背/肩/手臂/腿/核心)：杠铃 / 哑铃 / 徒手 / 器械 / 绳索 / 史密斯机 /
 *    壶铃 / 弹力带 / EZ曲杆 / 瑞士球 / 药球 / 雪橇 / 负重 / 滚轮 / 拉伸
 *  - 有氧：有氧 6 个子分类名（轻松稳态有氧等）+ 有氧器械
 *  - 其它/旧数据：""（全部）
 *
 * 数据集扩展字段（v7 新增，来源 exercises-dataset，文字部分 MIT 许可）：
 *  nameEn / target / secondaryMuscles / steps / note / sortWeight
 * 配图字段（v8 新增，个人使用，图片 © Gym visual）：
 *  imagePath（assets/exercise_images/ 下的 GIF 文件名；自定义导入图片则存应用私有目录绝对路径）
 * 收藏字段（v9 新增）：isFavorite，收藏的动作在列表中置顶展示
 */
@Entity(
    tableName = "exercises",
    foreignKeys = [
        ForeignKey(
            entity = BodyPart::class,
            parentColumns = ["id"],
            childColumns = ["bodyPartId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["bodyPartId"]), Index(value = ["subcategory"])]
)
data class Exercise(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val bodyPartId: Long,
    val name: String,
    val tips: String = "",
    val subcategory: String = "",
    val isCustom: Boolean = false,
    val createTime: Long = System.currentTimeMillis(),
    /** 英文原名（卡片副标题、搜索用），内置精选动作为空 */
    val nameEn: String = "",
    /** 主要目标肌群（中文） */
    val target: String = "",
    /** 次要肌群（中文，、分隔） */
    val secondaryMuscles: String = "",
    /** 分步动作要领（\n 分隔），来源数据集中文说明 */
    val steps: String = "",
    /** 名称括号里的补充说明（如「手臂伸直」「双腿屈膝」） */
    val note: String = "",
    /** 排序权重：0 = 精选/自定义（置顶），1 = 数据集扩展库 */
    val sortWeight: Int = 0,
    /**
     * 动作配图（GIF）在 assets/exercise_images/ 下的文件名，如 "0001-2gPfomN.gif"。
     * 空字符串表示无配图（个人使用，图片版权归 Gym visual，详见 ExerciseDatasetSeeder）。
     * 用户自行导入图片时，存应用私有目录绝对路径（如 "/data/.../exercise_images/custom_xxx.jpg"），
     * 加载器据此区分 assets 资源与本地文件。
     */
    val imagePath: String = "",
    /**
     * 是否收藏。收藏的动作在列表中置顶展示（SQL 排序 isFavorite DESC）。
     */
    val isFavorite: Boolean = false
) {

    /** 是否为数据集扩展动作 */
    val isFromDataset: Boolean get() = sortWeight > 0

    /** 分步要领列表 */
    fun stepList(): List<String> =
        if (steps.isBlank()) emptyList() else steps.split('\n').filter { it.isNotBlank() }

    /** 详情页展示用的要领文本：优先用户 tips，其次数据集分步说明 */
    fun displayTips(): String = when {
        tips.isNotBlank() -> tips
        steps.isNotBlank() -> stepList().mapIndexed { i, s -> "${i + 1}. $s" }.joinToString("\n")
        else -> ""
    }

    companion object {

        /** 有氧 6 个子分类（顺序即展示顺序） */
        val CARDIO_SUBCATEGORIES = listOf(
            "轻松稳态有氧",
            "间歇暴汗有氧",
            "球类运动",
            "健身操",
            "水上有氧",
            "耐力负重有氧"
        )

        /** 力量部位子分类（用户新建动作时的常用选项） */
        val STRENGTH_SUBCATEGORIES = listOf(
            "杠铃", "哑铃", "徒手", "器械", "绳索", "史密斯机",
            "壶铃", "弹力带", "EZ曲杆", "瑞士球", "药球", "雪橇", "负重", "滚轮", "拉伸"
        )

        /** 核心子分类 */
        val CORE_SUBCATEGORIES = listOf(
            "徒手", "器械", "绳索", "杠铃", "哑铃", "壶铃", "弹力带",
            "瑞士球", "药球", "负重", "滚轮", "史密斯机", "拉伸"
        )

        /**
         * 全局子分类展示顺序：子卡片按此顺序排列，
         * 实际显示哪些卡片由数据库中该部位真实存在的子分类决定（数据驱动）。
         */
        val SUBCATEGORY_ORDER = listOf(
            // 力量 / 器械维度
            "杠铃", "哑铃", "徒手", "器械", "绳索", "史密斯机", "壶铃", "弹力带",
            "EZ曲杆", "瑞士球", "药球", "雪橇", "负重", "滚轮",
            // 有氧
            "轻松稳态有氧", "间歇暴汗有氧", "球类运动", "健身操", "水上有氧",
            "耐力负重有氧", "有氧器械",
            // 柔韧放最后
            "拉伸"
        )

        /** 子分类排序索引，未收录的排最后 */
        fun subcategoryOrderIndex(sub: String): Int {
            val i = SUBCATEGORY_ORDER.indexOf(sub)
            return if (i >= 0) i else SUBCATEGORY_ORDER.size
        }

        /** 判断某部位是否需要子分类分组 */
        fun needsGrouping(bodyPartId: Long): Boolean = bodyPartId != CARDIO_BODY_PART_SORT

        const val CARDIO_BODY_PART_SORT = 7L
        const val NECK_BODY_PART_SORT = 8L

        // 预置动作数据（内置标准动作库）
        fun getPredefinedExercises(): List<Exercise> = listOf(
            // ===== 胸部 (bodyPartId=1) =====
            Exercise(bodyPartId = 1, name = "杠铃卧推", nameEn = "Barbell Bench Press", target = "胸大肌", subcategory = "杠铃", tips = "肩胛骨后缩下沉，核心收紧，杠铃下落至乳头上方，推起时肘关节不要完全锁死"),
            Exercise(bodyPartId = 1, name = "哑铃卧推", nameEn = "Dumbbell Bench Press", target = "胸大肌", subcategory = "哑铃", tips = "哑铃下落至胸部两侧，推起时哑铃不要相碰，保持张力"),
            Exercise(bodyPartId = 1, name = "上斜哑铃卧推", nameEn = "Incline Dumbbell Press", target = "上胸（胸大肌锁骨部）", subcategory = "哑铃", tips = "斜板角度30-45度，哑铃推至锁骨上方，注意控制离心"),
            Exercise(bodyPartId = 1, name = "上斜杠铃卧推", nameEn = "Incline Barbell Press", target = "上胸", subcategory = "杠铃", tips = "斜板上斜15-30度，重点刺激上胸，杠铃下放至锁骨下方"),
            Exercise(bodyPartId = 1, name = "下斜杠铃卧推", nameEn = "Decline Barbell Press", target = "下胸", subcategory = "杠铃", tips = "下斜角度15-30度，侧重下胸，注意控制下放避免过深"),
            Exercise(bodyPartId = 1, name = "哑铃飞鸟", nameEn = "Dumbbell Fly", target = "胸大肌", subcategory = "哑铃", tips = "肘关节微屈，想象拥抱大树，感受胸肌拉伸"),
            Exercise(bodyPartId = 1, name = "绳索夹胸", nameEn = "Cable Crossover", target = "胸大肌内侧", subcategory = "器械", tips = "身体微微前倾，手肘微屈，顶峰收缩1秒"),
            Exercise(bodyPartId = 1, name = "俯卧撑", nameEn = "Push-up", target = "胸大肌", subcategory = "徒手", tips = "身体呈一条直线，核心收紧，胸部尽量贴近地面"),
            Exercise(bodyPartId = 1, name = "宽距俯卧撑", nameEn = "Wide Push-up", target = "胸大肌外侧", subcategory = "徒手", tips = "双手略宽于肩，侧重胸大肌外侧，身体保持直线"),
            Exercise(bodyPartId = 1, name = "器械推胸", nameEn = "Chest Press Machine", target = "胸大肌", subcategory = "器械", tips = "背部贴紧靠垫，推出时呼气，还原时吸气"),
            Exercise(bodyPartId = 1, name = "史密斯机上斜推", nameEn = "Smith Machine Incline Press", target = "上胸", subcategory = "器械", tips = "轨迹固定，适合大重量，斜板夹角30度刺激上胸"),
            Exercise(bodyPartId = 1, name = "双杠臂屈伸（胸）", nameEn = "Chest Dip", target = "下胸 / 肱三头肌", subcategory = "徒手", tips = "身体前倾，肘部外展，感受下胸与三头协同发力"),
            Exercise(bodyPartId = 1, name = "地板卧推", nameEn = "Floor Press", target = "胸大肌 / 肱三头肌", subcategory = "杠铃", tips = "平躺地面，限制了动作幅度，强化三头推举力量"),
            Exercise(bodyPartId = 1, name = "哑铃上斜飞鸟", nameEn = "Incline Dumbbell Fly", target = "上胸", subcategory = "哑铃", tips = "斜板上斜，飞鸟时手肘微屈，控制下放感受上胸拉伸"),
            Exercise(bodyPartId = 1, name = "跪姿俯卧撑", nameEn = "Knee Push-up", target = "胸大肌", subcategory = "徒手", tips = "适合初学者的俯卧撑变式，注意核心收紧避免塌腰"),

            // ===== 背部 (bodyPartId=2) =====
            Exercise(bodyPartId = 2, name = "引体向上", nameEn = "Pull-up", target = "背阔肌", subcategory = "徒手", tips = "肩胛骨下沉后缩，胸部上提，避免借力摆动"),
            Exercise(bodyPartId = 2, name = "负重引体向上", nameEn = "Weighted Pull-up", target = "背阔肌", subcategory = "徒手", tips = "腰部挂重物或负重带，保持躯干稳定，避免晃动"),
            Exercise(bodyPartId = 2, name = "杠铃划船", nameEn = "Barbell Row", target = "背阔肌", subcategory = "杠铃", tips = "背部挺直，杠铃沿大腿拉向腹部，顶峰收缩"),
            Exercise(bodyPartId = 2, name = "高位下拉", nameEn = "Lat Pulldown", target = "背阔肌", subcategory = "器械", tips = "宽握，肩胛骨下沉后缩，拉至锁骨位置"),
            Exercise(bodyPartId = 2, name = "窄握高位下拉", nameEn = "Close-grip Lat Pulldown", target = "背阔肌下缘", subcategory = "器械", tips = "窄握反手，拉至胸部上方，强化背阔肌下缘"),
            Exercise(bodyPartId = 2, name = "坐姿划船", nameEn = "Seated Cable Row", target = "背阔肌", subcategory = "器械", tips = "挺胸收腹，拉手柄至腹部，感受背阔肌收缩"),
            Exercise(bodyPartId = 2, name = "直臂下压", nameEn = "Straight-arm Pulldown", target = "背阔肌", subcategory = "器械", tips = "身体微微前倾，手臂伸直下压，感受背阔肌拉伸"),
            Exercise(bodyPartId = 2, name = "单臂哑铃划船", nameEn = "One-arm Dumbbell Row", target = "背阔肌", subcategory = "哑铃", tips = "支撑手稳定，哑铃拉向髋部，避免旋转躯干"),
            Exercise(bodyPartId = 2, name = "硬拉", nameEn = "Deadlift", target = "下背 / 竖脊肌 / 臀腿", subcategory = "杠铃", tips = "杠铃贴近小腿，背部挺直，髋部主导发力"),
            Exercise(bodyPartId = 2, name = "罗马尼亚硬拉", nameEn = "Romanian Deadlift", target = "腘绳肌 / 下背", subcategory = "杠铃", tips = "膝盖微屈，髋部后移，感受腘绳肌拉伸（侧重下背与后链）"),
            Exercise(bodyPartId = 2, name = "T杠划船", nameEn = "T-bar Row", target = "背阔肌", subcategory = "杠铃", tips = "俯身握住T杠，拉向腹部，背部挺直，避免借力"),
            Exercise(bodyPartId = 2, name = "器械划船", nameEn = "Machine Row", target = "背阔肌", subcategory = "器械", tips = "调整胸垫高度，拉动手柄，顶峰收缩感受背阔肌"),
            Exercise(bodyPartId = 2, name = "绳索面拉划船", nameEn = "Face Pull", target = "三角肌后束 / 上背", subcategory = "器械", tips = "双手握绳索，拉向腹部，肩胛骨后缩，强化上背"),
            Exercise(bodyPartId = 2, name = "耸肩", nameEn = "Shrug", target = "斜方肌", subcategory = "徒手", tips = "肩膀垂直上提，顶峰停顿，强化斜方肌，避免旋转"),
            Exercise(bodyPartId = 2, name = "杠铃耸肩", nameEn = "Barbell Shrug", target = "斜方肌", subcategory = "杠铃", tips = "双臂自然下垂，肩膀垂直向上耸起，顶点停顿收缩"),
            Exercise(bodyPartId = 2, name = "反手杠铃划船", nameEn = "Reverse-grip Barbell Row", target = "背阔肌下缘 / 肱二头肌", subcategory = "杠铃", tips = "反手握杠能更好刺激背阔肌下缘和肱二头肌"),

            // ===== 肩部 (bodyPartId=3) =====
            Exercise(bodyPartId = 3, name = "哑铃肩推", nameEn = "Dumbbell Shoulder Press", target = "三角肌前束", subcategory = "哑铃", tips = "核心收紧，哑铃推至头顶上方，避免过度后仰"),
            Exercise(bodyPartId = 3, name = "杠铃肩推", nameEn = "Barbell Shoulder Press", target = "三角肌", subcategory = "杠铃", tips = "杠铃从锁骨推至头顶，核心收紧，避免腰部过度反弓"),
            Exercise(bodyPartId = 3, name = "坐姿哑铃肩推", nameEn = "Seated Dumbbell Press", target = "三角肌", subcategory = "哑铃", tips = "坐姿减少借力，哑铃推至头顶，控制下放"),
            Exercise(bodyPartId = 3, name = "侧平举", nameEn = "Lateral Raise", target = "三角肌中束", subcategory = "哑铃", tips = "肘关节微屈，手臂抬至与肩平齐，控制离心"),
            Exercise(bodyPartId = 3, name = "前平举", nameEn = "Front Raise", target = "三角肌前束", subcategory = "哑铃", tips = "交替或同时进行，手臂抬至与肩平齐"),
            Exercise(bodyPartId = 3, name = "俯身飞鸟", nameEn = "Bent-over Lateral Raise", target = "三角肌后束", subcategory = "哑铃", tips = "身体前倾，手臂微屈，向两侧抬起感受后束"),
            Exercise(bodyPartId = 3, name = "史密斯机肩推", nameEn = "Smith Machine Shoulder Press", target = "三角肌", subcategory = "器械", tips = "轨迹固定，适合大重量，注意控制下放"),
            Exercise(bodyPartId = 3, name = "面拉", nameEn = "Face Pull", target = "三角肌后束 / 斜方肌", subcategory = "器械", tips = "绳索拉向面部两侧，肩胛骨后缩，强化后束"),
            Exercise(bodyPartId = 3, name = "直立划船", nameEn = "Upright Row", target = "三角肌中束 / 斜方肌", subcategory = "杠铃", tips = "杠铃沿身体上提至下巴，肘部引导，避免耸肩"),
            Exercise(bodyPartId = 3, name = "绳索侧平举", nameEn = "Cable Lateral Raise", target = "三角肌中束", subcategory = "器械", tips = "单侧绳索，全程恒定张力，肘部微屈抬至肩高"),
            Exercise(bodyPartId = 3, name = "阿诺德推举", nameEn = "Arnold Press", target = "三角肌", subcategory = "哑铃", tips = "旋转式推举，起始掌心朝内，推起时外旋，全面刺激三角肌"),
            Exercise(bodyPartId = 3, name = "反向飞鸟（后束）", nameEn = "Reverse Fly", target = "三角肌后束", subcategory = "器械", tips = "俯身或趴凳，双臂向两侧展开，感受三角肌后束"),
            Exercise(bodyPartId = 3, name = "杠铃前平举", nameEn = "Barbell Front Raise", target = "三角肌前束", subcategory = "杠铃", tips = "单手或双手持杠铃，抬至与肩平齐，避免摆动借力"),
            Exercise(bodyPartId = 3, name = "Y字上举", nameEn = "Y-raise", target = "肩袖肌群 / 三角肌", subcategory = "徒手", tips = "双臂呈Y字上举，强化肩袖稳定肌群，重量宜轻"),

            // ===== 手臂 (bodyPartId=4) =====
            Exercise(bodyPartId = 4, name = "杠铃弯举", nameEn = "Barbell Curl", target = "肱二头肌", subcategory = "杠铃", tips = "大臂夹紧身体，避免借力摆动，顶峰收缩"),
            Exercise(bodyPartId = 4, name = "哑铃弯举", nameEn = "Dumbbell Curl", target = "肱二头肌", subcategory = "哑铃", tips = "交替或同时弯举，大臂固定，控制离心"),
            Exercise(bodyPartId = 4, name = "哑铃锤式弯举", nameEn = "Hammer Curl", target = "肱肌 / 肱桡肌", subcategory = "哑铃", tips = "掌心相对，同时锻炼肱肌和肱桡肌"),
            Exercise(bodyPartId = 4, name = "牧师凳弯举", nameEn = "Preacher Curl", target = "肱二头肌", subcategory = "器械", tips = "大臂固定，孤立刺激肱二头肌"),
            Exercise(bodyPartId = 4, name = "绳索弯举", nameEn = "Cable Curl", target = "肱二头肌", subcategory = "器械", tips = "绳索提供恒定张力，控制离心，顶峰收缩"),
            Exercise(bodyPartId = 4, name = "集中弯举", nameEn = "Concentration Curl", target = "肱二头肌", subcategory = "哑铃", tips = "大臂靠在大腿内侧，孤立刺激肱二头肌"),
            Exercise(bodyPartId = 4, name = "杠铃反手弯举", nameEn = "Reverse Barbell Curl", target = "肱桡肌 / 前臂", subcategory = "杠铃", tips = "反手握杠，侧重肱桡肌和前臂"),
            Exercise(bodyPartId = 4, name = "绳索下压", nameEn = "Triceps Pushdown", target = "肱三头肌", subcategory = "器械", tips = "大臂夹紧身体，小臂下压至伸直"),
            Exercise(bodyPartId = 4, name = "仰卧臂屈伸", nameEn = "Lying Triceps Extension", target = "肱三头肌", subcategory = "杠铃", tips = "大臂垂直地面，控制下放速度"),
            Exercise(bodyPartId = 4, name = "哑铃颈后臂屈伸", nameEn = "Overhead Dumbbell Extension", target = "肱三头肌", subcategory = "哑铃", tips = "双手或单手持哑铃，肘关节固定"),
            Exercise(bodyPartId = 4, name = "窄握卧推（三头）", nameEn = "Close-grip Bench Press", target = "肱三头肌", subcategory = "杠铃", tips = "窄握杠铃，肘部贴近身体，侧重肱三头肌"),
            Exercise(bodyPartId = 4, name = "双杠臂屈伸（三头）", nameEn = "Triceps Dip", target = "肱三头肌", subcategory = "徒手", tips = "身体直立，肘部向后，侧重肱三头肌"),
            Exercise(bodyPartId = 4, name = "绳索过头臂屈伸", nameEn = "Overhead Cable Extension", target = "肱三头肌", subcategory = "器械", tips = "绳索过头，肘关节固定，下压至伸直"),
            Exercise(bodyPartId = 4, name = "腕弯举", nameEn = "Wrist Curl", target = "前臂屈肌", subcategory = "徒手", tips = "前臂置于大腿，手腕向上弯举，强化前臂"),

            // ===== 腿部 (bodyPartId=5) =====
            Exercise(bodyPartId = 5, name = "杠铃深蹲", nameEn = "Barbell Squat", target = "股四头肌", subcategory = "杠铃", tips = "双脚与肩同宽，膝盖方向与脚尖一致，蹲至大腿平行地面"),
            Exercise(bodyPartId = 5, name = "前蹲", nameEn = "Front Squat", target = "股四头肌", subcategory = "杠铃", tips = "杠铃置于锁骨前，躯干更直立，侧重股四头肌"),
            Exercise(bodyPartId = 5, name = "高脚杯深蹲", nameEn = "Goblet Squat", target = "股四头肌", subcategory = "哑铃", tips = "双手持哑铃于胸前，适合新手学习深蹲姿势"),
            Exercise(bodyPartId = 5, name = "腿举", nameEn = "Leg Press", target = "股四头肌 / 臀大肌", subcategory = "器械", tips = "双脚位置决定刺激部位，宽位偏内收肌，窄位偏股四头肌"),
            Exercise(bodyPartId = 5, name = "腿弯举", nameEn = "Leg Curl", target = "腘绳肌", subcategory = "器械", tips = "俯卧或坐姿，控制离心，顶峰收缩"),
            Exercise(bodyPartId = 5, name = "腿屈伸", nameEn = "Leg Extension", target = "股四头肌", subcategory = "器械", tips = "调整座椅使膝盖对齐转轴，避免过度伸展"),
            Exercise(bodyPartId = 5, name = "提踵", nameEn = "Calf Raise", target = "小腿腓肠肌", subcategory = "徒手", tips = "站姿或坐姿，缓慢下放，充分拉伸小腿"),
            Exercise(bodyPartId = 5, name = "保加利亚分腿蹲", nameEn = "Bulgarian Split Squat", target = "股四头肌 / 臀大肌", subcategory = "徒手", tips = "后脚抬高，前腿主导发力，注意平衡"),
            Exercise(bodyPartId = 5, name = "箭步蹲", nameEn = "Lunge", target = "股四头肌 / 臀大肌", subcategory = "徒手", tips = "向前跨步下蹲，前膝盖不超脚尖，后膝接近地面"),
            Exercise(bodyPartId = 5, name = "罗马尼亚硬拉（腿）", nameEn = "Romanian Deadlift", target = "腘绳肌", subcategory = "杠铃", tips = "膝盖微屈，髋部后移，感受腘绳肌拉伸"),
            Exercise(bodyPartId = 5, name = "臀桥", nameEn = "Glute Bridge", target = "臀大肌", subcategory = "徒手", tips = "仰卧屈膝，臀部发力向上顶起，顶峰收缩"),
            Exercise(bodyPartId = 5, name = "杠铃臀冲", nameEn = "Barbell Hip Thrust", target = "臀大肌", subcategory = "杠铃", tips = "上背靠凳，杠铃置于髋部，臀部发力顶起"),
            Exercise(bodyPartId = 5, name = "山羊挺身", nameEn = "Back Extension", target = "竖脊肌 / 下背", subcategory = "徒手", tips = "调整挡板位置，动作幅度适中，感受下背部"),
            Exercise(bodyPartId = 5, name = "坐姿提踵", nameEn = "Seated Calf Raise", target = "小腿比目鱼肌", subcategory = "器械", tips = "坐姿负重提踵，充分拉伸小腿后侧"),
            Exercise(bodyPartId = 5, name = "站姿提踵", nameEn = "Standing Calf Raise", target = "小腿腓肠肌", subcategory = "器械", tips = "站姿负重，脚尖发力和小腿收缩，控制下放"),

            // ===== 核心 (bodyPartId=6) =====
            Exercise(bodyPartId = 6, name = "平板支撑", nameEn = "Plank", target = "腹横肌 / 核心", subcategory = "徒手", tips = "身体呈一条直线，核心收紧，避免塌腰或撅臀"),
            Exercise(bodyPartId = 6, name = "卷腹", nameEn = "Crunch", target = "腹直肌", subcategory = "徒手", tips = "下巴微收，用腹部力量卷起，不要拉脖子"),
            Exercise(bodyPartId = 6, name = "俄罗斯转体", nameEn = "Russian Twist", target = "腹斜肌", subcategory = "徒手", tips = "双脚离地，核心控制，左右转动"),
            Exercise(bodyPartId = 6, name = "悬垂举腿", nameEn = "Hanging Leg Raise", target = "下腹部", subcategory = "徒手", tips = "悬垂时身体稳定，用腹部力量抬腿"),
            Exercise(bodyPartId = 6, name = "山羊挺身（核心）", nameEn = "Back Extension (Core)", target = "竖脊肌", subcategory = "器械", tips = "调整挡板位置，动作幅度适中，重点刺激竖脊肌与下背部"),
            Exercise(bodyPartId = 6, name = "真空腹训练", nameEn = "Stomach Vacuum", target = "腹横肌", subcategory = "徒手", tips = "呼气后吸气，腹部向内收缩，保持10-15秒"),
            Exercise(bodyPartId = 6, name = "仰卧起坐", nameEn = "Sit-up", target = "腹直肌", subcategory = "徒手", tips = "脚固定，腹部发力卷起，避免用手拉头"),
            Exercise(bodyPartId = 6, name = "侧平板支撑", nameEn = "Side Plank", target = "腹斜肌", subcategory = "徒手", tips = "侧身支撑，身体呈直线，强化腹斜肌"),
            Exercise(bodyPartId = 6, name = "登山跑", nameEn = "Mountain Climber", target = "核心 / 心肺", subcategory = "徒手", tips = "俯撑交替提膝，保持核心稳定，动作快而稳"),
            Exercise(bodyPartId = 6, name = "死虫式", nameEn = "Dead Bug", target = "深层核心", subcategory = "徒手", tips = "仰卧，对侧手脚缓慢伸展，保持腰部贴地"),
            Exercise(bodyPartId = 6, name = "健腹轮", nameEn = "Ab Wheel Rollout", target = "腹直肌 / 核心", subcategory = "器械", tips = "跪姿推出健腹轮，核心控制回收，避免塌腰"),
            Exercise(bodyPartId = 6, name = "鸟狗式", nameEn = "Bird Dog", target = "核心稳定", subcategory = "徒手", tips = "四点支撑，对侧手脚伸展，保持躯干稳定"),
            Exercise(bodyPartId = 6, name = "反向卷腹", nameEn = "Reverse Crunch", target = "下腹部", subcategory = "徒手", tips = "仰卧，下腹部发力将骨盆卷起，双腿收向胸部"),
            Exercise(bodyPartId = 6, name = "绳索卷腹", nameEn = "Cable Crunch", target = "腹直肌", subcategory = "器械", tips = "跪姿绳索，从额角拉向膝盖，控制旋转腹部"),
            Exercise(bodyPartId = 6, name = "仰卧抬腿", nameEn = "Lying Leg Raise", target = "下腹部", subcategory = "徒手", tips = "仰卧双腿伸直抬起，下腹发力，腰部贴地"),

            // ===== 有氧 (bodyPartId=7, subcategory = 有氧 6 大子分类) =====
            // 一、轻松稳态有氧
            Exercise(bodyPartId = 7, name = "匀速慢跑", nameEn = "Steady Jog", target = "心肺 / 全身", subcategory = "轻松稳态有氧", tips = "腰背挺直，脚掌中足落地，匀速呼吸三步一吸三步一呼，避免大步砸脚伤膝盖"),
            Exercise(bodyPartId = 7, name = "跑步机快走", nameEn = "Treadmill Walk", target = "心肺 / 下肢", subcategory = "轻松稳态有氧", tips = "身体微微前倾，甩开手臂，坡度调 3~5 度，提升臀部参与度，省力燃脂效率更高"),
            Exercise(bodyPartId = 7, name = "休闲户外骑行", nameEn = "Leisure Cycling", target = "心肺 / 下肢", subcategory = "轻松稳态有氧", tips = "坐垫高度对齐髋骨，膝盖踩踏最低点微弯，上半身放松不要弯腰驼背"),
            Exercise(bodyPartId = 7, name = "椭圆机匀速有氧", nameEn = "Elliptical Trainer", target = "心肺 / 全身", subcategory = "轻松稳态有氧", tips = "手脚同步联动，全程脚掌贴紧踏板，不要踮脚，重心落在脚掌中部"),
            Exercise(bodyPartId = 7, name = "卧式单车慢骑", nameEn = "Recumbent Bike", target = "心肺 / 下肢", subcategory = "轻松稳态有氧", tips = "腰背贴紧靠背，保持匀速踩踏，适合膝盖不适人群长时间训练"),

            // 二、间歇暴汗有氧
            Exercise(bodyPartId = 7, name = "快慢交替冲刺跑", nameEn = "Sprint Intervals", target = "心肺", subcategory = "间歇暴汗有氧", tips = "快跑 1 分钟 + 慢走 2 分钟循环，冲刺加大摆臂，慢走调整呼吸，不要骤然停下"),
            Exercise(bodyPartId = 7, name = "跳绳间歇训练", nameEn = "Jump Rope Intervals", target = "心肺 / 小腿", subcategory = "间歇暴汗有氧", tips = "大臂贴身体，只用手腕摇绳，落地轻踮脚尖，间歇分组防止小腿过度粗壮"),
            Exercise(bodyPartId = 7, name = "HIIT 徒手循环（登山跑）", nameEn = "HIIT Bodyweight Circuit", target = "心肺 / 核心", subcategory = "间歇暴汗有氧", tips = "臀部下沉不要抬太高，核心全程绷紧，膝盖往胸口方向快速靠拢"),
            Exercise(bodyPartId = 7, name = "波比跳间歇", nameEn = "Burpee Intervals", target = "心肺 / 全身", subcategory = "间歇暴汗有氧", tips = "俯身平稳下俯，起跳顺势发力，新手简化去掉腾空跳跃，保护腰腹关节"),

            // 三、球类运动有氧
            Exercise(bodyPartId = 7, name = "半场篮球", nameEn = "Half-court Basketball", target = "心肺 / 全身", subcategory = "球类运动", tips = "跑动尽量用前脚掌缓冲，攻防间歇主动调整呼吸，避免剧烈跑动憋气"),
            Exercise(bodyPartId = 7, name = "羽毛球单打", nameEn = "Badminton Singles", target = "心肺 / 全身", subcategory = "球类运动", tips = "屈膝沉髋启动脚步，大范围跨步时膝盖不超过脚尖，防止扭伤"),
            Exercise(bodyPartId = 7, name = "休闲足球", nameEn = "Casual Soccer", target = "心肺 / 下肢", subcategory = "球类运动", tips = "短途折返跑放缓落地节奏，避免急停猛转损伤脚踝"),
            Exercise(bodyPartId = 7, name = "网球休闲对战", nameEn = "Recreational Tennis", target = "心肺 / 全身", subcategory = "球类运动", tips = "侧身转体发力，不要单靠手臂挥拍，减少肩颈劳损"),

            // 四、健身操有氧
            Exercise(bodyPartId = 7, name = "尊巴 Zumba 燃脂操", nameEn = "Zumba", target = "心肺 / 全身", subcategory = "健身操", tips = "放松胯部跟随节拍扭动，重心左右平稳切换，动作不用刻意追求标准，保证持续活动"),
            Exercise(bodyPartId = 7, name = "居家网红燃脂操", nameEn = "Home Fat-burn Dance", target = "心肺 / 全身", subcategory = "健身操", tips = "全程收紧核心，跳跃类动作轻落地，楼层住户尽量垫瑜伽垫减震"),
            Exercise(bodyPartId = 7, name = "基础有氧健身操", nameEn = "Basic Aerobics", target = "心肺 / 全身", subcategory = "健身操", tips = "四肢动作舒展，配合均匀呼吸，新手放慢节拍跟上节奏即可"),

            // 五、水上有氧
            Exercise(bodyPartId = 7, name = "蛙泳长距离游", nameEn = "Long-distance Breaststroke", target = "心肺 / 全身", subcategory = "水上有氧", tips = "收腿外翻脚尖，蹬夹并拢连贯，匀速换气避免抬头游造成腰酸"),
            Exercise(bodyPartId = 7, name = "自由泳稳态游泳", nameEn = "Steady Freestyle", target = "心肺 / 全身", subcategory = "水上有氧", tips = "侧身转头换气，躯干保持平直，依靠转体带动手臂划水"),
            Exercise(bodyPartId = 7, name = "水中踏步有氧", nameEn = "Aqua Step", target = "心肺 / 下肢", subcategory = "水上有氧", tips = "水深至胸口，大幅度抬膝踏步，借助水阻锻炼下肢，完全无落地冲击"),

            // 六、耐力负重有氧
            Exercise(bodyPartId = 7, name = "农夫行走", nameEn = "Farmer's Walk", target = "核心 / 握力 / 全身", subcategory = "耐力负重有氧", tips = "双手握紧哑铃垂于身体两侧，沉肩收腹，目视前方匀速大步直行"),
            Exercise(bodyPartId = 7, name = "负重背包徒步", nameEn = "Weighted Hiking", target = "心肺 / 下肢", subcategory = "耐力负重有氧", tips = "重物贴合后背肩胛骨位置，不要下坠压腰，腰背全程挺直"),
            Exercise(bodyPartId = 7, name = "力量动作循环有氧", nameEn = "Strength Circuit", target = "心肺 / 全身", subcategory = "耐力负重有氧", tips = "多个力量动作无间断依次完成，组间短休息，依靠持续肌肉负荷做耐力有氧")
        )

        /** 有氧子分类卡片的简要说明（用于子分类卡片页的副标题） */
        fun cardioSubcategoryDesc(sub: String): String = when (sub) {
            "轻松稳态有氧" -> "低强度长时间，适配日常减脂"
            "间歇暴汗有氧" -> "短时高强度，后燃减脂"
            "球类运动" -> "娱乐性强，依靠跑动达成有氧消耗"
            "健身操" -> "跟随音乐律动"
            "水上有氧" -> "零关节压力，膝盖伤病首选"
            "耐力负重有氧" -> "力量爱好者专用，兼顾力量 + 有氧"
            "有氧器械" -> "单车/椭圆机/登山梯，强度可精确控制"
            else -> ""
        }

        /** 力量/器械维度子分类卡片的简要说明 */
        fun strengthSubcategoryDesc(sub: String): String = when (sub) {
            "杠铃" -> "自由重量之王，适合大重量复合动作"
            "哑铃" -> "左右独立可控，动作行程更自由"
            "徒手" -> "无需器械，随时随地开练"
            "器械" -> "固定轨迹，安全易上手"
            "绳索" -> "全程恒定张力，孤立刺激更充分"
            "史密斯机" -> "轨迹受限更稳，可安全冲大重量"
            "壶铃" -> "摆荡与爆发，力量心肺一起练"
            "弹力带" -> "便携抗阻，末端张力最大"
            "EZ曲杆" -> "曲杆握距自然，减轻腕肘压力"
            "瑞士球" -> "不稳定平面，附带核心刺激"
            "药球" -> "抛掷发力，功能性与爆发力"
            "雪橇" -> "推拉负重前行，腿臀与心肺双杀"
            "负重" -> "自重动作加负荷，突破瓶颈"
            "滚轮" -> "长杠杆离心，核心硬核挑战"
            "拉伸" -> "放松筋膜，改善柔韧与活动度"
            else -> ""
        }

        /** 核心部位子分类卡片的简要说明 */
        fun coreSubcategoryDesc(sub: String): String = when (sub) {
            "徒手" -> "自重核心训练，随时随地可练"
            "器械" -> "借助器械强化核心与躯干稳定性"
            else -> strengthSubcategoryDesc(sub)
        }

        /**
         * 根据部位返回该部位「可能」展示的子分类候选（新建动作选择器用）。
         * 子卡片页实际展示哪些，由数据库真实数据决定（见 sortSubcategories）。
         */
        fun subcategoriesForBodyPart(bodyPartName: String): List<String> = when (bodyPartName) {
            "有氧" -> CARDIO_SUBCATEGORIES + "有氧器械"
            "核心" -> CORE_SUBCATEGORIES
            else -> STRENGTH_SUBCATEGORIES
        }

        /**
         * 把数据库里查出来的子分类按全局展示顺序排序（数据驱动子卡片）
         */
        fun sortSubcategories(subs: Collection<String>): List<String> =
            subs.filter { it.isNotBlank() }.distinct().sortedBy { subcategoryOrderIndex(it) }

        /**
         * 根据部位 + 子分类，返回卡片简述（用于子分类卡片副标题）
         */
        fun subcategoryDescForBodyPart(bodyPartName: String, sub: String): String = when (bodyPartName) {
            "有氧" -> cardioSubcategoryDesc(sub).ifBlank { strengthSubcategoryDesc(sub) }
            "核心" -> coreSubcategoryDesc(sub)
            else -> strengthSubcategoryDesc(sub)
        }
    }
}
