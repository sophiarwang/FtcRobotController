package overcharged.pedroPathing.localization.localizers;


import android.util.Log;

import com.qualcomm.robotcore.hardware.HardwareMap;
import com.qualcomm.robotcore.util.ElapsedTime;
import com.qualcomm.robotcore.util.RobotLog;

import org.firstinspires.ftc.robotcore.external.navigation.AngleUnit;
import org.firstinspires.ftc.robotcore.external.navigation.DistanceUnit;
import org.firstinspires.ftc.robotcore.external.navigation.Pose2D;
import overcharged.pedroPathing.localization.GoBildaPinpointDriver;
import overcharged.pedroPathing.localization.Localizer;
import overcharged.pedroPathing.localization.Pose;
import overcharged.pedroPathing.pathGeneration.MathFunctions;
import overcharged.pedroPathing.pathGeneration.Vector;
import overcharged.pedroPathing.util.NanoTimer;
import org.opencv.core.Mat;

/**
 * This is the Pinpoint class. This class extends the Localizer superclass and is a
 * localizer that uses the two wheel odometry set up with the IMU to have more accurate heading
 * readings. The diagram below, which is modified from Road Runner, shows a typical set up.
 *
 * The view is from the top of the robot looking downwards.
 *
 * left on robot is the y positive direction
 *
 * forward on robot is the x positive direction
 *
 *                    forward (x positive)
 *                                △
 *                                |
 *                                |
 *                         /--------------\
 *                         |              |
 *                         |              |
 *                         |           || |
 *  left (y positive) <--- |           || |
 *                         |     ____     |
 *                         |     ----     |
 *                         \--------------/
 * With the pinpoint your readings will be used in mm
 * to use inches ensure to divide your mm value by 25.4
 *
 * @author Logan Nash
 * @author Havish Sripada 12808 - RevAmped Robotics
 * @author Ethan Doak - Gobilda
 * @version 1.0, 10/2/2024
 */
public class PinpointLocalizer extends Localizer {
    private HardwareMap hardwareMap;
    private Pose startPose;
    private GoBildaPinpointDriver odo;
    private double previousHeading;
    private double totalHeading;
    private long deltaTimeNano;
    private NanoTimer timer;
    private Pose currentVelocity;
    private Pose previousPinpointPose;


    /**
     * This creates a new PinpointLocalizer from a HardwareMap, with a starting Pose at (0,0)
     * facing 0 heading.
     *
     * @param map the HardwareMap
     */
    public PinpointLocalizer(HardwareMap map){ this(map, new Pose());}

    /**
     * This creates a new PinpointLocalizer from a HardwareMap and a Pose, with the Pose
     * specifying the starting pose of the localizer.
     *
     * @param map the HardwareMap
     * @param setStartPose the Pose to start from
     */
    public PinpointLocalizer(HardwareMap map, Pose setStartPose){
        hardwareMap = map;
        // TODO: replace this with your Pinpoint port
        odo = hardwareMap.get(GoBildaPinpointDriver.class,"pinpoint");

        //This uses mm, to use inches divide these numbers by 25.4
        setOffsets(5.8,0.8 , DistanceUnit.INCH); //these are tuned for 3110-0002-0001 Product Insight #1
        //57.35, 62.35
        //TODO: If you find that the gobilda Yaw Scaling is incorrect you can edit this here
        odo.setYawScalar(0.98);
        //TODO: Set your encoder resolution here, I have the Gobilda Odometry products already included.
        //TODO: If you would like to use your own odometry pods input the ticks per mm in the commented part below
        odo.setEncoderResolution(GoBildaPinpointDriver.GoBildaOdometryPods.goBILDA_4_BAR_POD);
        //odo.setEncoderResolution(13.26291192);
        //TODO: Set encoder directions
        odo.setEncoderDirections(GoBildaPinpointDriver.EncoderDirection.REVERSED, GoBildaPinpointDriver.EncoderDirection.FORWARD);

        odo.resetPosAndIMU();

        setStartPose(setStartPose);
        totalHeading = 0;
        timer = new NanoTimer();
        previousPinpointPose = new Pose();
        currentVelocity = new Pose();
        deltaTimeNano = 1;
        previousHeading = setStartPose.getHeading();
    }
    /**
     * This returns the current pose estimate.
     *
     * @return returns the current pose estimate as a Pose
     */
    @Override
    public Pose getPose() {
        return MathFunctions.addPoses(startPose, MathFunctions.rotatePose(previousPinpointPose, startPose.getHeading(), false));
    }

    /**
     * This returns the current velocity estimate.
     *
     * @return returns the current velocity estimate as a Pose
     */
    @Override
    public Pose getVelocity() {
        return currentVelocity.copy();
    }

    /**
     * This returns the current velocity estimate.
     *
     * @return returns the current velocity estimate as a Vector
     */
    @Override
    public Vector getVelocityVector() {
        return currentVelocity.getVector();
    }

    /**
     * This sets the start pose. Since nobody should be using this after the robot has begun moving,
     * and due to issues with the PinpointLocalizer, this is functionally the same as setPose(Pose).
     *
     * @param setStart the new start pose
     */
    @Override
    public void setStartPose(Pose setStart) {
        this.startPose = setStart;
    }

    /**
     * This sets the current pose estimate. Changing this should just change the robot's current
     * pose estimate, not anything to do with the start pose.
     *
     * @param setPose the new current pose estimate
     */
    @Override
    public void setPose(Pose setPose) {
        Pose setNewPose = MathFunctions.subtractPoses(setPose, startPose);
        odo.setPosition(new Pose2D(DistanceUnit.INCH, setNewPose.getX(), setNewPose.getY(), AngleUnit.RADIANS, setNewPose.getHeading()));
    }

    /**
     * This updates the total heading of the robot. The Pinpoint handles all other updates itself.
     */
    @Override
    public void update() {
        deltaTimeNano = timer.getElapsedTime();
        timer.resetTimer();
        odo.update();
        Pose2D pinpointPose = odo.getPosition();
        Pose currentPinpointPose = new Pose(pinpointPose.getX(DistanceUnit.INCH), pinpointPose.getY(DistanceUnit.INCH), pinpointPose.getHeading(AngleUnit.RADIANS));
        totalHeading += MathFunctions.getSmallestAngleDifference(currentPinpointPose.getHeading(), previousHeading);
        previousHeading = currentPinpointPose.getHeading();
        Pose deltaPose = MathFunctions.subtractPoses(currentPinpointPose, previousPinpointPose);
        currentVelocity = new Pose(deltaPose.getX() / (deltaTimeNano / Math.pow(10.0, 9)), deltaPose.getY() / (deltaTimeNano / Math.pow(10.0, 9)), deltaPose.getHeading() / (deltaTimeNano / Math.pow(10.0, 9)));
        previousPinpointPose = currentPinpointPose;
    }

    /**
     * This returns how far the robot has turned in radians, in a number not clamped between 0 and
     * 2 * pi radians. This is used for some tuning things and nothing actually within the following.
     *
     * @return returns how far the robot has turned in total, in radians.
     */
    @Override
    public double getTotalHeading() {
        return totalHeading;
    }

    /**
     * This returns the Y encoder value as none of the odometry tuners are required for this localizer
     * @return returns the Y encoder value
     */
    @Override
    public double getForwardMultiplier() {
        return odo.getEncoderY();
    }

    /**
     * This returns the X encoder value as none of the odometry tuners are required for this localizer
     * @return returns the X encoder value
     */
    @Override
    public double getLateralMultiplier() {
        return odo.getEncoderX();
    }

    /**
     * This returns either the factory tuned yaw scalar or the yaw scalar tuned by yourself.
     * @return returns the yaw scalar
     */
    @Override
    public double getTurningMultiplier() {
        return odo.getYawScalar();
    }

    /**
     * This sets the offsets and converts inches to millimeters
     * @param xOffset How far to the side from the center of the robot is the x-pod? Use positive values if it's to the left and negative if it's to the right.
     * @param yOffset How far forward from the center of the robot is the y-pod? Use positive values if it's forward and negative if it's to the back.
     * @param unit The units that the measurements are given in
     */
    private void setOffsets(double xOffset, double yOffset, DistanceUnit unit) {
        odo.setOffsets(unit.toMm(xOffset), unit.toMm(yOffset));
    }

    /**
     * This resets the IMU.
     */
    @Override
    public void resetIMU()  {
        odo.recalibrateIMU();

        try {
            Thread.sleep(300);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * This resets the OTOS.
     */
    private void resetPinpoint() {
        odo.resetPosAndIMU();

        try {
            Thread.sleep(300);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}