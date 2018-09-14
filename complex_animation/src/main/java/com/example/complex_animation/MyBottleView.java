package com.example.complex_animation;

import android.content.Context;
import android.graphics.Camera;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.CornerPathEffect;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.RectF;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

/**
 * 最后时间 2018年9月14日 17:06:24
 * <p>
 * 控件类：装水的瓶，水会动；
 * <p>
 * 实现思路：
 * 1）画瓶身
 * 左半边
 * 1- 瓶嘴 2- 瓶颈  3- 瓶身  4- 瓶底
 * 右半边：使用矩阵变换，复制左边部分的path
 * <p>
 * 2）画瓶中的水.采用逆时针绘制顺序
 * 1-左边的弧形
 * 2-瓶底直线
 * 3-右边弧形
 * 4-右边的小段二阶贝塞尔曲线
 * 5-中间的大段三阶贝塞尔曲线
 * 6-左边的小段二阶贝塞尔曲线
 *
 * 主要技术点：
 * 1）Path类的应用，包括绝对坐标定位，相对坐标定位添加 contour,
 * 2)PathMeasure类的应用，计算当前path对象的上某个点的坐标
 * 3)贝塞尔曲线的应用
 *
 * 主要难点：
 * 1）画波浪的时候，三段贝塞尔曲线的控制点的确定，多端贝塞尔曲线的完美相切
 * 2) 画瓶身的时候，矩阵变换 实现path的翻转复制；
 * 3) 三角函数的应用，····其实不是难，是老了，这些东西不记得了,而且反应慢 ，囧~~~
 *
 * emmm···其他的，想不起来了，应该没了吧；所有技术点，难点，可以在我的代码中找到解决方案
 */
public class MyBottleView extends View {

    private Context mContext;

    public MyBottleView(Context context) {
        this(context, null);
    }

    public MyBottleView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public MyBottleView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mContext = context;
    }

    private Paint mBottlePaint, mWaterPaint, mPointPaint;//三支画笔
    private Path mBottlePath, mWaterPath;//两条path，一个画瓶身，一个画瓶中的水

    private static final int DEFAULT_WATER_COLOR = 0XFF41EDFA;//水的颜色
    private static final int DEFAULT_BOTTLE_COLOR = 0XFFCEFCFF;//瓶身颜色

    private float startX, startY;//绘制起点的X,Y

    //尺寸变量
    private float paintWidth;//画笔的宽度
    private float bottleMouthRadius;//瓶嘴小弯曲的直径
    private float bottleMouthOffSetX;//瓶嘴小弯曲的X轴矫正
    private float bottleBodyArcRadius;//瓶身弧形半径
    private float bottleNeckWidth;//瓶颈宽度
    private float bottleMouthConnectLineX;//瓶嘴和瓶颈连接处的小短线 X偏移量
    private float bottleMouthConnectLineY;//瓶嘴和瓶颈连接处的小短线 Y偏移量
    private float bottleNeckHeight;// 瓶颈高度

    //尺寸变量 相对于 参照值的半分比
    private float paintWidthPercent;//画笔的宽度
    private float bottleMouthRadiusPercent;//瓶嘴小弯曲的直径(占比)
    private float bottleMouthOffSetXPercent;//瓶嘴小弯曲的X轴矫正(占比)
    private float bottleMouthConnectLineXPercent;//瓶嘴和瓶颈连接处的小短线 X偏移量(占比)
    private float bottleMouthConnectLineYPercent;//瓶嘴和瓶颈连接处的小短线 Y偏移量(占比)
    private float bottleNeckWidthPercent;//瓶颈宽度(占比)
    private float bottleNeckHeightPercent;// 瓶颈高度(占比)
    private float bottleBodyArcRadiusPercent;//瓶身弧形半径(占比)

    private float referenceValue = 300;//参照值，因为我画图形原型的时候，是用300dp的宽高做的参照

    //角度,角度不需要适配
    private float bottleMouthStartAngle;// 瓶嘴弧形的开始角度值
    private float bottleMouthSweepAngle;// 瓶嘴弧形横扫角度
    private float bottleBodyStartAngle;// 瓶身弧形的开始角度值
    private float bottleBodySweepAngle;// 瓶身弧形横扫角度

    int mWidth, mHeight;//控件的宽高
    //保存 瓶身矩形的左上角右下角坐标
    float bottleBodyArcLeft;
    float bottleBodyArcTop;
    float bottleBodyArcRight;
    float bottleBodyArcBottom;
    private double bottleBottomSomeContour;//瓶底，除了瓶颈宽度之外的2个小段的长度

    //按比例划分中间的波浪形态
    private float rightQuadLengthRatio = 0.02f;//右边二阶曲线的长度比例
    private float midCubicLengthRatio = 0.96f;//中间三阶曲线的长度比例
    private float leftQuadLengthRatio = 0.02f;//左边二阶曲线的长度比例

    //由于 左右两个二阶曲线的Y轴控制点是要变化的(为了让波浪两端显得更加柔和)，所以用全局变量保存偏移量
    private float rightQuadControlPointOffsetY;
    private float leftQuadControlPointOffsetY;

    private float centerCubicControlX_1 = 0.225f;//中间三阶曲线的第一个控制点X，
    private float centerCubicControlY_1 = -0.3f;//中间三阶曲线的第一个控制点X，
    private float centerCubicControlX_2 = 0.675f;//中间三阶曲线的第一个控制点X，
    private float centerCubicControlY_2 = 0.3f;//中间三阶曲线的第一个控制点X，
    private float waterLeftRatio = 0.15f;//水面抬高的比例,要让动画变得柔和，就要把水面稍微抬高一点点
    private float paramDelta = 0.005f;//每次刷新水面时的 参数变动值,用来控制动画的频率

    private boolean ifShowSupportPoints = true;//是否要开启辅助点

    /**
     * 画笔初始化
     */
    private void initPaint() {
        mBottlePaint = new Paint();
        mBottlePaint.setAntiAlias(true);
        mBottlePaint.setStyle(Paint.Style.STROKE);
        mBottlePaint.setColor(DEFAULT_BOTTLE_COLOR);
        //柔和的特殊处理
        mBottlePaint.setStrokeCap(Paint.Cap.ROUND);//画直线的时候，头部变成圆角
        CornerPathEffect mBottleCornerPathEffect = new CornerPathEffect(paintWidth);//在直线和直线的交界处自动用圆角处理,圆角直径20
        mBottlePaint.setPathEffect(mBottleCornerPathEffect);
        mBottlePaint.setStrokeWidth(paintWidth);//画笔宽度

        //画水
        mWaterPaint = new Paint();
        mWaterPaint.setAntiAlias(true);
        mWaterPaint.setStyle(Paint.Style.FILL);
        mWaterPaint.setColor(DEFAULT_WATER_COLOR);
        mWaterPaint.setStrokeCap(Paint.Cap.ROUND);//画直线的时候，头部变成圆角
        mWaterPaint.setPathEffect(mBottleCornerPathEffect);
        mWaterPaint.setStrokeWidth(paintWidth);//画笔宽度

        //画辅助点
        mPointPaint = new Paint();
        mPointPaint.setAntiAlias(true);
        mPointPaint.setStyle(Paint.Style.STROKE);
        if (ifShowSupportPoints) {
            mPointPaint.setColor(Color.YELLOW);
        } else {
            mPointPaint.setColor(Color.TRANSPARENT);
        }
        mPointPaint.setStrokeWidth(paintWidth * 1);//画笔宽度
    }

    /**
     * 为了做全自动适配，将我测试过程中用到的dp值，都转变成 小数百分比, 使用的时候，再根据用乘法转化成实际的dp值
     */
    private void initPercents() {

        paintWidthPercent = 2 / referenceValue;
        bottleMouthRadiusPercent = 3 / referenceValue;
        bottleMouthOffSetXPercent = 2 / referenceValue;
        bottleMouthConnectLineXPercent = 2 / referenceValue;
        bottleMouthConnectLineYPercent = 5 / referenceValue;

        bottleNeckWidthPercent = 30 / referenceValue;
        bottleNeckHeightPercent = 100 / referenceValue;

        bottleBodyArcRadiusPercent = 80 / referenceValue;
    }


    /**
     * 初始化宽高
     */
    private void initWH() {
        mWidth = getWidth();
        mHeight = getHeight();
    }

    /**
     * 比例值已经上一步中已经设定好了，现在将比例值，转化成实际的长度
     */
    private void initParams() {
        float realValue = DpUtil.px2dp(mContext, mWidth > mHeight ? mHeight : mWidth);//以较宽高中较小的那一项为准，现在设置的值都以这个为参照，
        bottleMouthRadius = DpUtil.dp2Px(mContext, bottleMouthRadiusPercent * realValue);//瓶嘴小弯曲的直径
        bottleMouthOffSetX = DpUtil.dp2Px(mContext, bottleMouthOffSetXPercent * realValue);//瓶嘴小弯曲的X轴矫正
        bottleMouthConnectLineX = DpUtil.dp2Px(mContext, bottleMouthConnectLineXPercent * realValue);//瓶嘴和瓶颈连接处的小短线 X偏移量
        bottleMouthConnectLineY = DpUtil.dp2Px(mContext, bottleMouthConnectLineYPercent * realValue);//瓶嘴和瓶颈连接处的小短线 Y偏移量
        bottleNeckWidth = DpUtil.dp2Px(mContext, bottleNeckWidthPercent * realValue);//瓶颈宽度
        bottleNeckHeight = DpUtil.dp2Px(mContext, bottleNeckHeightPercent * realValue);// 瓶颈高度
        bottleBodyArcRadius = DpUtil.dp2Px(mContext, bottleBodyArcRadiusPercent * realValue);//瓶身弧形半径
        paintWidth = DpUtil.dp2Px(mContext, paintWidthPercent * realValue);// 画笔

        //弧形的角度
        bottleMouthStartAngle = -90;// 瓶嘴弧形的开始角度值
        bottleMouthSweepAngle = -120;// 瓶嘴弧形横扫角度

        bottleBodyStartAngle = -90;// 瓶身弧形的开始角度值
        bottleBodySweepAngle = -160;// 瓶身弧形横扫角度

        startX = mWidth / 2 - bottleNeckWidth / 2; // 绘制起点的X,Y
        startY = (mHeight - bottleNeckHeight - bottleBodyArcRadius * 2) / 2;//起点位置的Y

    }

    /**
     * 计算瓶身path, 并且绘制出来
     */
    private void calculateBottlePath(Canvas canvas) {
        if (mBottlePath == null) {
            mBottlePath = new Path();
        } else {
            mBottlePath.reset();
        }
        addPartLeft();//左边一半
        addPartRight();//右边一半

        canvas.drawPath(mBottlePath, mBottlePaint);//画瓶子
    }


    /**
     * 画左边那一半,主要是用Path，add直线，add弧线，组合起来，就是一条不规则曲线
     */
    private void addPartLeft() {
        mBottlePath = new Path();
        mBottlePath.moveTo(startX, startY);//移动path到开始绘制的位置

        //先画一个弧线，瓶子最上方的小嘴
        RectF r = new RectF();
        r.set(startX - bottleMouthOffSetX, startY, startX - bottleMouthOffSetX + bottleMouthRadius * 2, startY + bottleMouthRadius * 2);//用矩阵定位弧形;
        mBottlePath.addArc(r, bottleMouthStartAngle, bottleMouthSweepAngle);//瓶嘴的小弯曲，画弧形-  解释一下这里为什么是-90：弧形的绘制 角度为0的位置是X轴的正向，而我们要从Y正向开始绘制； 划过角度是-120的意思是，逆时针旋转120度。

        mBottlePath.rLineTo(bottleMouthConnectLineX, bottleMouthConnectLineY);//瓶颈和小弯曲的连接处直线
        mBottlePath.rLineTo(0, bottleNeckHeight);//瓶颈直线

        float[] pos = new float[2];//终点的坐标,0 位置是X，1位置是Y
        calculateLastPartOfPathEndingPos(mBottlePath, pos);//这个pos的值在执行了这一行之后已经发生了改变 , 这个pos就是结束坐标，里面存了x和y

        //然后再画瓶身
        RectF r2 = new RectF();

        bottleBodyArcLeft = pos[0] - bottleBodyArcRadius;
        bottleBodyArcTop = pos[1];
        bottleBodyArcRight = pos[0] + bottleBodyArcRadius;
        bottleBodyArcBottom = pos[1] + bottleBodyArcRadius * 2;

        r2.set(bottleBodyArcLeft, bottleBodyArcTop, bottleBodyArcRight, bottleBodyArcBottom);//原来绘制矩阵还有这个说法,先定 左上角和右下角的坐标；

        mBottlePath.addArc(r2, bottleBodyStartAngle, bottleBodySweepAngle);//弧形瓶身

        bottleBottomSomeContour = Math.sin(Math.toRadians(180 - Math.abs(bottleBodySweepAngle))) * bottleBodyArcRadius;//由于上面的弧度并没有划过180度，所以，会有剩余的角度对应着一段X方向的距离
        // 上面的弧形画完了，下面接着弧形的这个终点，画直线
        mBottlePath.rLineTo(bottleNeckWidth / 2 + (float) bottleBottomSomeContour * 1.2f, 0);//瓶底
    }

    /**
     * 右边这一半其实是左边一半的镜像，沿着左边那一半右边线，向右翻转180度，就像翻书一样
     */
    private void addPartRight() {
        //由于是对称图形，所以··复制左边的mPath就行了；
        Camera camera = new Camera();//看Camera类的注释就知道，Camera实例是用来计算3D转换，以及生成一个可用的矩阵(比如给Canvas用)
        Matrix matrix = new Matrix();
        camera.save();//保存当前状态，save和restore是配套使用的
        camera.rotateY(180);//旋转180度，相当于照镜子，复制镜像,但是这里只是指定了旋转的度数，并没有指定旋转的轴，
        // 所以我也是很疑惑，旋转中心轴是怎么弄的；属性动画的旋转轴，应该就是控件的中心线（沿着x轴旋转，就是用Y的中垂线作为轴；沿着Y轴旋转，就是用X的中垂线做轴）
        // 这里的旋转不是在控件层面，而是在 path层面，所以，要手动指定旋转轴
        camera.getMatrix(matrix);//计算矩阵坐标到当前转换，以及 复制它到 参数matrix对象中；
        camera.restore();//还原状态

        //设置矩阵旋转的轴；因为我复制出来的path，是和左边那一半覆盖的，而我要将以一条竖线往右翻转180度，达到复制镜像的目的
        float rotateX = startX + bottleNeckWidth / 2;//旋转的轴线的X坐标

        matrix.preTranslate(-rotateX, 0);//由于是Y轴方向上的旋转，而且只是想复制镜像，原来path的Y轴坐标不需要改变，所以这里dy传0就好了
        matrix.postTranslate(rotateX, 0);//其实这里还有很多骚操作，闲的蛋疼的话可以改参数玩一下
        //原来这个矩阵变换，是给旋转做参数的么
        //矩阵matrix已经好了,现在把矩阵对象设置给这个path
        Path rightBottlePath = new Path();
        rightBottlePath.addPath(mBottlePath);//复制左边的路径；不影响参数path对象

        //这里解释一下这两个参数：
        // 其一，rightBottlePath，它是右边那一半的路径
        // 其二，matrix，这个是一个矩阵对象，它在本案例中的就是 控制一个旋转中心点的作用；
        mBottlePath.addPath(rightBottlePath, matrix);
    }

    /**
     * 计算直线的最终坐标
     *
     * @param mPath
     * @param pos
     */
    private void calculateLastPartOfPathEndingPos(Path mPath, float[] pos) {
        PathMeasure pathMeasure = new PathMeasure();
        pathMeasure.setPath(mPath, false);
        pathMeasure.getPosTan(pathMeasure.getLength(), pos, new float[2]);//找出终点的位置
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        initWH();
        initPercents();
        initParams();
        initPaint();

        updateWaterFlowParams();

        calculateBottlePath(canvas);
        calculateWaterPath(canvas);

        invalidate();//不停刷新自己
    }


    /**
     * 计算瓶中的水的path并且绘制出来
     * <p>
     * 思路，整个path是逆时针的添加元素的；
     * 添加的顺序是 一段弧线arc，一段直线line，一段弧线arc，一段二阶曲线quad，一段三阶曲线cubic，一段二阶曲线quad
     * <p>
     * 这里我采用的是相对坐标定位，以path当前的点为基准，设定目标点的相对坐标，使用的方法都是r开头的，比如rLine，arcTo,rQuad 等
     */
    private void calculateWaterPath(Canvas canvas) {
        if (mWaterPath == null)
            mWaterPath = new Path();
        else
            mWaterPath.reset();

        //从瓶身左侧开始，逆时针绘制,
        float margin = paintWidth * 3;
        RectF leftArcRect = new RectF(bottleBodyArcLeft + margin, bottleBodyArcTop + margin, bottleBodyArcRight - margin, bottleBodyArcBottom - margin);
        mWaterPath.arcTo(leftArcRect, -180, -70f);//左侧一个逆时针的70度圆弧
        mWaterPath.rLineTo(((float) bottleBottomSomeContour * 2 + bottleNeckWidth), 0);//从左到右的直线

        //右侧圆弧
        //然后是弧线；由于我先画的是右半边的弧线，所以，矩形定位要用左半边的矩形坐标来转换
        float left = bottleBodyArcLeft + bottleNeckWidth;
        float top = bottleBodyArcTop;
        float right = bottleBodyArcLeft + bottleBodyArcRadius * 2 + bottleNeckWidth;//
        float bottom = bottleBodyArcBottom;
        RectF rightArcRect = new RectF(left + margin, top + margin, right - margin, bottom - margin);
        mWaterPath.arcTo(rightArcRect, 70f, -70f);

        //右边弧线画完之后的坐标
        float rightArcEndX = leftArcRect.left + leftArcRect.width() + bottleNeckWidth;
        float rightArcEndY = leftArcRect.top + leftArcRect.height() / 2;

        float waterFaceWidth = bottleBodyArcRadius * 2 + bottleNeckWidth - margin * 2;//水面的横向长度

        // 直接用一整段3阶贝塞尔曲线，结果发现，和边界的连接点不圆滑；
        // 替换方案:从右到左，整个曲线分为三段，第一段是二阶曲线，长度比例为0.05；第二段是三阶曲线，长度比例0.9，第三段是  二阶曲线，长度比例为0.05
        // 1、先用一段二阶曲线，连接右边界的点，和 中间三阶曲线的起点
        float right_endX = -waterFaceWidth * rightQuadLengthRatio;// 右边一段曲线的X横跨长度
        float right_endY = -bottleBodyArcRadius * waterLeftRatio;//右边二阶曲线的终点位置

        float right_controlX = right_endX * 0f;//右边的二阶曲线的控制点X
        float right_controlY = right_endY * 1;//右边的二阶曲线的控制点Y

        // 2、贝塞尔曲线的终点相对坐标
        // 画3阶曲线作为主波浪
        float relative_controlX1 = -waterFaceWidth * centerCubicControlX_1;//控制点的相对坐标X
        float relative_controlY1 = -bottleBodyArcRadius * centerCubicControlY_1;//控制点的相对坐标y

        float relative_controlX2 = -waterFaceWidth * centerCubicControlX_2;//控制点的相对坐标X
        float relative_controlY2 = -bottleBodyArcRadius * centerCubicControlY_2;//控制点的相对坐标y

        float relative_endX = -waterFaceWidth * midCubicLengthRatio;//中间三阶曲线的横向长度
        float relative_endY = 0;

        // 3、再用一段二阶曲线来封闭图形
        // 我还得根据那个矩形，算出起点位置
        float leftQuadLineEndX = -waterFaceWidth * leftQuadLengthRatio;
        float leftQuadLineEndY = bottleBodyArcRadius * waterLeftRatio;

        float left_controlX = leftQuadLineEndX * 1;//左边的二阶曲线的控制点X
        float left_controlY = leftQuadLineEndY * 0;//左边的二阶曲线的控制点Y

        float[] pos = new float[2];//终点的坐标,0 位置是X，1位置是Y
        calculateLastPartOfPathEndingPos(mWaterPath, pos);//这个pos的值在执行了这一行之后已经发生了改变 , 这个pos就是结束坐标，里面存了x和y

        //下面全部采用的相对坐标，都是以当前的点为基准的相对坐标
        mWaterPath.rQuadTo(right_controlX, right_controlY + rightQuadControlPointOffsetY, right_endX, right_endY);//右边的二阶曲线

        float[] pos2 = new float[2];//终点的坐标,0 位置是X，1位置是Y
        calculateLastPartOfPathEndingPos(mWaterPath, pos2);//这个pos的值在执行了这一行之后已经发生了改变 , 这个pos就是结束坐标，里面存了x和y

        mWaterPath.rCubicTo(relative_controlX1, relative_controlY1, relative_controlX2, relative_controlY2, relative_endX, relative_endY);

        float[] pos3 = new float[2];//终点的坐标,0 位置是X，1位置是Y
        calculateLastPartOfPathEndingPos(mWaterPath, pos3);//这个pos的值在执行了这一行之后已经发生了改变 , 这个pos就是结束坐标，里面存了x和y
        mWaterPath.rQuadTo(left_controlX, left_controlY - leftQuadControlPointOffsetY, leftQuadLineEndX, leftQuadLineEndY);//用绝对坐标的二阶曲线，封闭图形；

        canvas.drawPath(mWaterPath, mWaterPaint);//画瓶子内的水

        canvas.drawPoint(rightArcEndX, rightArcEndY, mPointPaint);//右边弧线画完之后的终点,同时也是右边二阶曲线的起点
        canvas.drawPoint(pos[0] + right_endX, pos[1] + right_endY, mPointPaint);//右边弧线画完之后的终点

        canvas.drawPoint(pos2[0] + relative_controlX1, pos2[1] + relative_controlY1, mPointPaint);//三阶曲线的右边控制点
        canvas.drawPoint(pos2[0] + relative_controlX2, pos2[1] + relative_controlY2, mPointPaint);//三阶曲线的左边控制点

        canvas.drawPoint(pos3[0] + leftQuadLineEndX, pos3[1] + leftQuadLineEndY, mPointPaint);//左边一小段二阶曲线的终点
        canvas.drawPoint(pos3[0], pos3[1], mPointPaint);//左边一小段二阶曲线的起点

        //我的目标，就是确定一个斜率
        float offsetX = waterFaceWidth * rightQuadLengthRatio;
        float x1 = pos2[0] + relative_controlX1;
        float y1 = pos2[1] + relative_controlY1;

        float x2 = pos[0] + right_endX;
        float y2 = pos[1] + right_endY;

        rightQuadControlPointOffsetY = calControlPointOffsetY(x1, y1, x2, y2, offsetX);
        canvas.drawPoint(pos[0] + right_controlX, pos[1] + right_controlY + calControlPointOffsetY(x1, y1, x2, y2, offsetX), mPointPaint);//右边一小段二阶曲线的控制点(是逆时针的曲线)

        //算出左边的
        offsetX = waterFaceWidth * rightQuadLengthRatio;
        x1 = pos2[0] + relative_controlX2;
        y1 = pos2[1] + relative_controlY2;

        x2 = pos3[0];
        y2 = pos3[1];

        leftQuadControlPointOffsetY = calControlPointOffsetY(x1, y1, x2, y2, offsetX);//把这两个值保存起来，下次刷新的时候用
        canvas.drawPoint(pos3[0] + left_controlX, pos3[1] + left_controlY - calControlPointOffsetY(x1, y1, x2, y2, offsetX), mPointPaint);//左边一小段二阶曲线的控制点

    }

    /**
     * 计算出控制点的Y轴偏移量
     *
     * @param x1      第一个点X
     * @param y1      第一个点Y
     * @param x2      第二个点X
     * @param y2      第二个点Y
     * @param offsetX 已知的X轴偏移量
     * @return
     */
    private float calControlPointOffsetY(float x1, float y1, float x2, float y2, float offsetX) {
        float tan = (y2 - y1) / (x2 - x1);//斜率
        float offsetY = offsetX * tan;
        return offsetY;
    }

    //辅助类
    private ParamObj obj2, obj3;//看ParamObj的注釋;

    /**
     * 改变水流参数，来实现水面的动态效果
     */
    private void updateWaterFlowParams() {

        if (obj2 == null) {
            obj2 = new ParamObj(-0.3f, false);
        }
        if (obj3 == null) {
            obj3 = new ParamObj(0.3f, true);
        }

        centerCubicControlY_1 = calParam(-0.6f, 0.6f, obj2);
        centerCubicControlY_2 = calParam(-0.6f, 0.6f, obj3);
    }

    /**
     * 做一个方法，让数字在两个范围之内变化，比如，从0到100，然后100到0，然后0到100；
     *
     * @param min
     * @param max
     * @param currentObj
     * @return
     */
    private float calParam(float min, float max, ParamObj currentObj) {
        if (currentObj.param >= min && currentObj.param <= max) {//如果在范围之内，就按照原来的方向，继续变化
            if (currentObj.ifReverse) {
                currentObj.param = currentObj.param + paramDelta;
            } else {
                currentObj.param = currentObj.param - paramDelta;
            }
        } else if (currentObj.param == max) {//如果到了最大值，就变小
            currentObj.ifReverse = true;
        } else if (currentObj.param == min) {//如果到了最小值，就变大
            currentObj.ifReverse = false;
        } else if (currentObj.param > max) {
            currentObj.param = max;
            currentObj.ifReverse = false;
        } else if (currentObj.param < min) {
            currentObj.param = min;
            currentObj.ifReverse = true;
        }
        Log.d("calParam", "" + currentObj.param);
        return currentObj.param;
    }

    class ParamObj {
        Float param;
        Boolean ifReverse;//是否反向(设定：true为数字递增，false为递减)

        /**
         * @param original  初始值
         * @param ifReverse 初始顺序
         */
        ParamObj(float original, boolean ifReverse) {
            this.param = original;
            this.ifReverse = ifReverse;
        }
    }


}
