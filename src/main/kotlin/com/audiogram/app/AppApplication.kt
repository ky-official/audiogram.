package com.audiogram.app

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class AppApplication

fun main(args: Array<String>) {

    System.getProperties().setProperty("sun.java2d.opengl", "true")
    System.getProperties().setProperty("sun.java2d.accthreshold", "0")
    System.getProperties().setProperty("sun.java2d.renderer", "org.marlin.pisces.MarlinRenderingEngine")

    runApplication<AppApplication>(*args)
}
