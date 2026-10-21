/*
 * Adapted from Flutter scroll_physics.dart, scroll_simulation.dart and
 * spring_simulation.dart, Copyright 2014 The Flutter Authors.
 * BSD-3-Clause; see assets/licenses/FLUTTER-BSD-3-Clause.txt.
 */
package com.akira.app.core.design.motion

import kotlin.math.*

internal object BouncingScrollPhysics {
    // BouncingScrollPhysics.frictionFactor / _applyFriction, normal deceleration.
    fun userOffset(overscroll: Double, viewport: Double, delta: Double): Double {
        if (overscroll == 0.0 || delta == 0.0) return delta
        val past=abs(overscroll)
        val easing=overscroll*delta<0.0
        val fraction=(if(easing)past-abs(delta) else past)/viewport.coerceAtLeast(1.0)
        val gamma=.52*(1-fraction).pow(2)
        if(gamma<=0.0)return 0.0
        val toLimit=past/gamma
        return sign(delta)*if(abs(delta)<toLimit)abs(delta)*gamma else past+abs(delta)-toLimit
    }
    // FrictionSimulation(.135, position, velocity), constantDeceleration=0.
    private val dragLog=ln(.135)
    fun frictionPosition(velocity: Double, time: Double)=velocity*(exp(dragLog*time)-1.0)/dragLog
    fun frictionVelocity(velocity: Double, time: Double)=velocity*exp(dragLog*time)

    // ScrollPhysics._kDefaultSpring: mass=.5, stiffness=100, damping ratio=1.1.
    // Analytic overdamped solution, not a spring tuned by eye.
    class ReturnSpring(position: Double, velocity: Double) {
        private val mass=.5
        private val stiffness=100.0
        private val damping=1.1*2*sqrt(mass*stiffness)
        private val discriminant=sqrt(damping*damping-4*mass*stiffness)
        private val r1=(-damping-discriminant)/(2*mass)
        private val r2=(-damping+discriminant)/(2*mass)
        private val c2=(velocity-r1*position)/(r2-r1)
        private val c1=position-c2
        fun position(time:Double)=c1*exp(r1*time)+c2*exp(r2*time)
        fun velocity(time:Double)=c1*r1*exp(r1*time)+c2*r2*exp(r2*time)
        // Flutter's logical-pixel tolerances, expressed here in physical pixels.
        fun done(time:Double)=abs(position(time))<1.0 && abs(velocity(time))<20.0
    }
}
