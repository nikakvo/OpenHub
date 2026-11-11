package com.thirtydegreesray.openhub.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AttachmentsTest {

    @Test
    public void contentTypeFromNameThenFromPhoneType() {
        assertEquals("image/png", Attachments.contentType("Screenshot.PNG", null));
        assertEquals("image/jpeg", Attachments.contentType("photo.jpeg", "application/octet-stream"));
        assertEquals("video/quicktime", Attachments.contentType("clip.mov", null));
        assertEquals("video/webm", Attachments.contentType("rec.webm", null));
        assertEquals("image/svg+xml", Attachments.contentType("logo.svg", null));
        // no extension: the type the phone reports
        assertEquals("video/mp4", Attachments.contentType("1000012345", "video/mp4"));
        assertEquals("image/jpeg", Attachments.contentType("IMG", "image/jpg"));
        // not taken by GitHub
        assertNull(Attachments.contentType("notes.pdf", "application/pdf"));
        assertNull(Attachments.contentType("photo.heic", "image/heic"));
        assertNull(Attachments.contentType(null, null));
    }

    @Test
    public void fileNameGetsTheMatchingExtension() {
        assertEquals("Screenshot.png", Attachments.fileName("Screenshot.png", "image/png"));
        assertEquals("photo.jpeg", Attachments.fileName("photo.jpeg", "image/jpeg"));
        assertEquals("1000012345.mp4", Attachments.fileName("1000012345", "video/mp4"));
        assertEquals("a_b.png", Attachments.fileName("a/b", "image/png"));
        assertEquals("file.gif", Attachments.fileName("  ", "image/gif"));
    }

    @Test
    public void sizeLimits() {
        assertFalse(Attachments.tooBig("image/png", 10L * 1024 * 1024));
        assertTrue(Attachments.tooBig("image/png", 10L * 1024 * 1024 + 1));
        assertFalse(Attachments.tooBig("video/mp4", 50L * 1024 * 1024));
        assertTrue(Attachments.tooBig("video/mp4", 100L * 1024 * 1024 + 1));
        // unknown size: GitHub decides
        assertFalse(Attachments.tooBig("image/png", -1));
    }

    @Test
    public void uploadUrlIsEncoded() {
        assertEquals("https://uploads.github.com/user-attachments/assets?repository_id=702340428"
                        + "&name=my%20shot%20%C3%A4.png&content_type=image%2Fpng",
                Attachments.uploadUrl(702340428L, "my shot ä.png", "image/png"));
    }

    @Test
    public void writeAccess() {
        assertTrue(Attachments.canUpload(false, false, true));
        assertTrue(Attachments.canUpload(false, true, false));
        assertTrue(Attachments.canUpload(true, false, false));
        assertFalse(Attachments.canUpload(false, false, false));
    }

    @Test
    public void markdownLikeGitHub() {
        String url = "https://github.com/user-attachments/assets/e051af73";
        assertEquals("![Screenshot_1](" + url + ")",
                Attachments.markdown("Screenshot_1.png", url, "image/png"));
        assertEquals("![a  b](" + url + ")", Attachments.markdown("a[]b.gif", url, "image/gif"));
        // a video is the bare link (GitHub shows a player)
        assertEquals(url, Attachments.markdown("clip.mp4", url, "video/mp4"));
    }

    @Test
    public void insertAsAParagraphOfItsOwn() {
        Attachments.Insertion empty = Attachments.insert("", 0, 0, "X");
        assertEquals("X\n\n", empty.text);
        assertEquals(3, empty.cursor);

        Attachments.Insertion end = Attachments.insert("Hello", 5, 5, "X");
        assertEquals("Hello\n\nX\n\n", end.text);
        assertEquals(10, end.cursor);

        assertEquals("Hello\n\nX\n\n world", Attachments.insert("Hello world", 5, 5, "X").text);

        // one line break before: one more; an empty line before: nothing more
        Attachments.Insertion lineStart = Attachments.insert("a\nb", 2, 2, "X");
        assertEquals("a\n\nX\n\nb", lineStart.text);
        assertEquals(6, lineStart.cursor);
        assertEquals("a\n\nX\n\nb", Attachments.insert("a\n\nb", 3, 3, "X").text);

        // line breaks after are counted, the cursor goes past them
        Attachments.Insertion beforeBreak = Attachments.insert("a\n", 1, 1, "X");
        assertEquals("a\n\nX\n\n", beforeBreak.text);
        assertEquals(6, beforeBreak.cursor);
        Attachments.Insertion beforeEmptyLine = Attachments.insert("a\n\nb", 1, 1, "X");
        assertEquals("a\n\nX\n\nb", beforeEmptyLine.text);
        assertEquals(6, beforeEmptyLine.cursor);

        // a selection is replaced; no cursor (-1) means the end
        assertEquals("a\n\nX\n\nc", Attachments.insert("abc", 1, 2, "X").text);
        assertEquals("abc\n\nX\n\n", Attachments.insert("abc", -1, -1, "X").text);
    }

    @Test
    public void videoLinks() {
        String video = "https://github.com/user-attachments/assets/39b1c65a-4212-4dd5-a9d0-56d0144fd275";
        assertTrue(Attachments.isVideoLink(video));
        assertTrue(Attachments.isVideoLink(" " + video + " "));
        // phone keyboards capitalise the first letter
        assertTrue(Attachments.isVideoLink("Https://GitHub.com/user-attachments/assets/39B1C65A-4212-4dd5-a9d0-56d0144fd275"));
        assertFalse(Attachments.isVideoLink(video + "/x"));
        assertFalse(Attachments.isVideoLink("https://github.com/user-attachments/files/123/log.txt"));
        assertFalse(Attachments.isVideoLink("https://github.com/nikakvo/OpenHub"));
        assertFalse(Attachments.isVideoLink(null));
        // a picture uploaded here is not a video; a video uploaded here is
        String picture = "https://github.com/user-attachments/assets/2f53e9fa-73ea-47ee-b845-d8436a15904f";
        Attachments.rememberLocalCopy(picture, "content://x/1", "image/jpeg");
        assertFalse(Attachments.isVideoLink(picture));
        assertEquals("content://x/1", Attachments.localCopy(picture));
        String clip = "https://github.com/user-attachments/assets/a1f9bf6c-2872-485f-b2a8-1bc0fd068dc4";
        Attachments.rememberLocalCopy(clip, "content://x/2", "video/mp4");
        assertTrue(Attachments.isVideoLink(clip));
    }

    @Test
    public void problems() {
        assertEquals(Attachments.Problem.NO_WRITE_ACCESS, Attachments.problem(404, "Not Found"));
        assertEquals(Attachments.Problem.NOT_ACCEPTED, Attachments.problem(403, null));
        assertEquals(Attachments.Problem.NOT_ACCEPTED, Attachments.problem(401, "Bad credentials"));
        assertEquals(Attachments.Problem.RATE_LIMITED, Attachments.problem(429, null));
        assertEquals(Attachments.Problem.GITHUB_MESSAGE, Attachments.problem(422, "File is too big"));
        assertEquals(Attachments.Problem.OTHER, Attachments.problem(500, ""));
    }
}
