import com.pranvir.blotto.*
import java.io.File
fun main() {
    val out = File("/home/claude/blotto/shots/audio").apply { mkdirs() }
    for (id in (0 until Sfx.COUNT) + Sfx.PROJECTOR) {
        val t0 = System.nanoTime(); val pcm = Synth.render(id); val ms = (System.nanoTime() - t0) / 1e6
        var peak = 0; var sum = 0.0
        for (s in pcm) { val a = Math.abs(s.toInt()); if (a > peak) peak = a; sum += s * s.toDouble() }
        val rms = Math.sqrt(sum / pcm.size)
        check(peak in 3000..32767) { "sfx $id peak $peak" }
        File(out, "sfx$id.wav").writeBytes(Synth.wav(pcm))
        println("sfx %3d  %.2fs  peak=%5d rms=%5.0f  gen=%.0fms".format(id, pcm.size / 22050f, peak, rms, ms))
    }
    val t0 = System.nanoTime(); val m = Synth.renderMusic(); val ms = (System.nanoTime() - t0) / 1e6
    File(out, "music.wav").writeBytes(Synth.wav(m))
    val jump = Math.abs(m[m.size - 1] - m[0])
    println("music %.2fs gen=%.0fms loop-seam jump=%d".format(m.size / 22050f, ms, jump))
}
