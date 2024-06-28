package commands

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent


object Experiments {

    // if 290 is 1200XP, then 100XP is = 290/100 = 2.9
    // the progress bar should follow this logic
    // make 290 equal to all the XP the rank requires to level up
    // allow the bar to be split into 100 parts
    // each part is (290/100) = 2.9
    // create an alghoritm that calculates the progress bar size
    private fun getProgressBarWidth(currentXP: Long, maxXP: Long): Float {
        println("currentXP: $currentXP")
        println("maxXP: $maxXP")
        val xpToLevelUp = maxXP - currentXP
        val xpPerPart = maxXP / 100
        val partsLeft = xpToLevelUp / xpPerPart
        val widthPerPart =
            2.9f // This is the result of : 290 / 100 where 290 is the width of the base progress bar and 100 is the number of parts the bar is split into
        return widthPerPart * partsLeft
    }

    private val ALLOWED_GUILDS = arrayListOf(182551016309260288L, 717239989863186512L)
    var status = false

    fun experiment(event: SlashCommandInteractionEvent) {
        if (status) {
            newRankExperiment(event)
        } else {
            event.hook.editOriginal("Sorry, but **Experiments** is currently unavailable.").queue()
            return
        }
    }

    fun newRankExperiment(event: SlashCommandInteractionEvent) {
        if (event.guild == null) {
            event.reply("Sorry, but the current experiment is only available in a guild.").queue()
            return
        }
        if (event.guild!!.idLong !in ALLOWED_GUILDS) {
            event.reply("Sorry, but feedback is not being gathered in this server at the moment.").queue()
            return
        }
        if (!status) {
            event.reply("Sorry, but **Experiments** is currently unavailable.").queue()
            return
        }
        /*
                val currentXP = Ranks.getMemberExp(event.user.id, event.guild!!.id)
                val calculate = Ranks.CalculateLevel(currentXP)
                val maxXP = Ranks.CalculateLevel(currentXP).total
                val nextLevel = calculate.level + 1
                val progressBarWidth = getProgressBarWidth(calculate.leftover, maxXP)
                val currentRankRoleColor = Ranks.getClosestAvailableRankID(event.user, event.guild!!.id)?.let {
                    event.guild!!.getRoleById(it)?.color ?: event.guild!!.selfMember.color
                }


                val avatar: BufferedImage = event.member?.avatarUrl?.let { ImageIO.read(URL(it)) }
                    ?: ImageIO.read(URL(event.user.avatarUrl ?: event.user.defaultAvatarUrl))

                val scaled: BufferedImage = Scalr.resize(avatar, 132)
                // get dominant color from image as array of R G B values. Ignore alpha channel
                val dominantColor = scaled.getRGB(0, 0, scaled.width, scaled.height, null, 0, scaled.width)
                    .map { it and 0xFFFFFF }
                    .groupingBy { it }
                    .eachCount()
                    .maxByOrNull { it.value }
                    ?.key
                    ?.let { intArrayOf(it shr 16, (it shr 8) and 0xFF, it and 0xFF) }
                    ?: intArrayOf(0, 0, 0)

                val skiaLayer = SkiaLayer()
                skiaLayer.skikoView = GenericSkikoView(skiaLayer, object : SkikoView {
                    val discordAlmostBlackColor = Color.makeRGB(40, 43, 48)
                    val appleBorderInside = Paint().apply { //needs to be 1px or 2px smaller than the main rectangle
                        color = Color.makeARGB(38, 255, 255, 255) // 15% of 255 is 38
                        strokeCap = PaintStrokeCap.ROUND
                        mode = PaintMode.STROKE
                        strokeWidth = 1f
                    }
                    val appleBorderOutside = Paint().apply {
                        color = Color.makeARGB(166, 0, 0, 0) // 65% of 255 is 166
                        strokeCap = PaintStrokeCap.ROUND
                        mode = PaintMode.STROKE
                        strokeWidth = 0.5f
                    }
                    val appleShadow = Paint().apply {
                        imageFilter =
                            ImageFilter.makeDropShadow(0f, 18f, 50f, 50f, Color.makeARGB(133, 0, 0, 0)) //52% of 255 is 133
                    }
                    val blackPaint = Paint().apply {
                        color = Color.makeRGB(0, 0, 0)
                    }
                    val whitePaint = Paint().apply {
                        color = Color.makeRGB(255, 255, 255)
                    }

                    override fun onRender(canvas: Canvas, width: Int, height: Int, nanoTime: Long) {
                        // canvas.clear(discordAlmostBlackColor)
                        val offset = 2f
                        RRect.makeXYWH(offset + 0f, offset + 0f, 600f, 200f, 100f).let {
                            // canvas.drawRRect(it, appleShadow)
                            canvas.drawRRect(it, Paint().apply {
                                color = Color.makeRGB(30, 33, 36)
                            })
                            canvas.drawRRect(it, appleBorderOutside)
                        }
                        RRect.makeXYWH(offset + 1f, offset + 1f, 598f, 198f, 100f).let {
                            canvas.drawRRect(it, appleBorderInside)
                        }

                        canvas.drawCircle(offset + 100f, offset + 100f, 66f, Paint().apply {
                            color = Color.makeRGB(dominantColor[0], dominantColor[1], dominantColor[2])
                            strokeCap = PaintStrokeCap.ROUND
                            mode = PaintMode.STROKE
                            strokeWidth = 4f
                        })
                        canvas.save()
                        canvas.clipPath(Path().apply {
                            addCircle(offset + 100f, offset + 100f, 66f)
                        })
                        // dowscale image to 132x132
                        // use imgscalr to do this
                        canvas.drawImage(scaled.toImage(), 34f, 34f)
                        canvas.restore()

                        Font(Typeface.makeFromName("Inter", FontStyle.BOLD), 18f).let {
                            val text = event.user.name  // "justinthedog"
                            canvas.drawString(text, offset + 210f, offset + 77f, it, whitePaint)
                        }
                        Font(Typeface.makeFromName("Inter", FontStyle.NORMAL), 18f).let {
                            val text = "#${event.user.discriminator}" // "#7100"
                            canvas.drawString(text, offset + 210f, offset + 98f, it, whitePaint)
                        }
                        Font(Typeface.makeFromName("Inter", FontStyle(800, 0, FontSlant.UPRIGHT)), 18f).let {
                            val text =
                                "RANK ${Ranks.getRankPosition(event.user, event.guild!!.id)}" //todo: rank placement e.g. 100
                            canvas.drawString(
                                text,
                                offset + 500f - it.measureTextWidth(text, blackPaint),
                                offset + 77f,
                                it,
                                whitePaint
                            )
                        }
                        Font(Typeface.makeFromName("Inter", FontStyle.BOLD), 11f).let {
                            val fLeftover = Ranks.formatNumber(calculate.leftover)
                            val fTotal = Ranks.formatNumber(calculate.total)
                            val text = "${fLeftover}/${fTotal}"  // "100/1200XP"
                            canvas.drawString(
                                text,
                                offset + 355f - (it.measureTextWidth(text, blackPaint) / 2),
                                offset + 153f,
                                it,
                                whitePaint
                            )
                        }
                        // if 290 is 1200XP, then 100XP is = 290/100 = 2.9
                        // the progress bar should follow this logic
                        // make 290 equal to all the XP the rank requires to level up
                        // allow the bar to be split into 100 parts
                        // each part is (290/100) = 2.9
                        // create an algorithm to calculate the progress bar width
                        RRect.makeXYWH(offset + 210f, offset + 155f, 290f, 11f, 22f).let {
                            canvas.drawRRect(it, Paint().apply {
                                color = Color.makeRGB(255, 255, 255)
                                strokeCap = PaintStrokeCap.ROUND
                                mode = PaintMode.STROKE
                                strokeWidth = 1f
                            })
                        }

                        println("progressBarWidth: $progressBarWidth")
                        RRect.makeXYWH(offset + 212f, offset + 157f, progressBarWidth, 7f, 22f).let {
                            canvas.drawRRect(it, Paint().apply {
                                // get the color of their rank role
                                // get the closest rank level registered in the database
                                color = if (currentRankRoleColor == null)
                                    Color.makeRGB(255, 255, 255)
                                else
                                    Color.makeRGB(
                                        currentRankRoleColor.red,
                                        currentRankRoleColor.green,
                                        currentRankRoleColor.blue
                                    )
                            })
                        }

                        Font(Typeface.makeFromName("Inter", FontStyle.BOLD), 11f).let {
                            val text = calculate.level // "60"
                            canvas.drawString(text.toString(), offset + 210f, offset + 176f, it, whitePaint)
                        }
                        Font(Typeface.makeFromName("Inter", FontStyle.BOLD), 11f).let {
                            canvas.drawString(
                                nextLevel.toString(),
                                offset + 500f - it.measureTextWidth(nextLevel.toString(), blackPaint),
                                offset + 176f,
                                it,
                                whitePaint
                            )
                        }

                    }
                })

                SwingUtilities.invokeLater {

                    val window = JFrame("").apply {
                        preferredSize = Dimension(604, 204)
                        size = preferredSize
                    }
                    window.add(skiaLayer, BorderLayout.CENTER)
                    window.isUndecorated = true
                    // skiaLayer.attachTo(window.contentPane)
                    skiaLayer.needRedraw()
                    window.pack()
                    window.isVisible = true

                    val scope = CoroutineScope(Dispatchers.Default)
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            SwingUtilities.invokeAndWait {
                                val image = skiaLayer.
                                    //convert this image to a bytearray
                                ImageIO.read(image)


                                skiaLayer.dispose()
                                window.dispose()
                                val arrayByte = ByteArrayOutputStream()
                                ImageIO.write(image, "png", arrayByte)

                                FileUpload.fromData(arrayByte.toByteArray(), "${event.user.asTag}.png").use { fileup ->
                                    event.hook.editOriginal(MessageEditData.fromFiles(fileup)).mentionRepliedUser(false)
                                        .queue()
                                }
                            }
                        }
                    }
                }*/
    }
}
