package org.magnum.dataup;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.io.IOUtils;
import org.magnum.dataup.model.Video;
import org.magnum.dataup.model.VideoStatus;
import org.magnum.dataup.model.VideoStatus.VideoState;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class VideoController {

	private static final AtomicLong currentId = new AtomicLong(0L);
	private Map<Long,Video> videos = new HashMap<Long, Video>();
	
	/*
	GET /video
	   - Returns the list of videos that have been added to the
	     server as JSON. The list of videos does not have to be
	     persisted across restarts of the server. The list of
	     Video objects should be able to be unmarshalled by the
	     client into a Collection<Video>.
	   - The return content-type should be application/json, which
	     will be the default if you use @ResponseBody
	     */
	@GetMapping("/video")
	@ResponseBody
	public Collection<Video> getVideos() {
		return videos.values();
	}
	
	/*     
	POST /video
	   - The video metadata is provided as an application/json request
	     body. The JSON should generate a valid instance of the 
	     Video class when deserialized by Spring's default 
	     Jackson library.
	   - Returns the JSON representation of the Video object that
	     was stored along with any updates to that object made by the server. 
	   - **_The server should generate a unique identifier for the Video
	     object and assign it to the Video by calling its setId(...)
	     method._** 
	   - No video should have ID = 0. All IDs should be > 0.
	   - The returned Video JSON should include this server-generated
	     identifier so that the client can refer to it when uploading the
	     binary mpeg video content for the Video.
	   - The server should also generate a "data url" for the
	     Video. The "data url" is the url of the binary data for a
	     Video (e.g., the raw mpeg data). The URL should be the _full_ URL
	     for the video and not just the path (e.g., http://localhost:8080/video/1/data would
	     be a valid data url). See the Hints section for some ideas on how to
	     generate this URL.
	 */
	@PostMapping("/video")
	@ResponseBody
	public Video postVideo(@RequestBody Video entity) {
		//set id
		if(entity.getId() == 0)
			entity.setId(currentId.incrementAndGet());
		//set url
		String url = getUrlBaseForLocalServer() + "/video/" + entity.getId() + "/data";
		entity.setDataUrl(url);
		//save
		videos.put(entity.getId(), entity);
		return entity;
	}
	
	private String getUrlBaseForLocalServer() {
		HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
		String base = "http://"+request.getServerName() + ((request.getServerPort() != 80) ? ":"+request.getServerPort() : "");
		return base;
	}
	
	/*
	POST /video/{id}/data
	   - The binary mpeg data for the video should be provided in a multipart
	     request as a part with the key "data". The id in the path should be
	     replaced with the unique identifier generated by the server for the
	     Video. A client MUST *create* a Video first by sending a POST to /video
	     and getting the identifier for the newly created Video object before
	     sending a POST to /video/{id}/data. 
	   - The endpoint should return a VideoStatus object with state=VideoState.READY
	     if the request succeeds and the appropriate HTTP error status otherwise.
	     VideoState.PROCESSING is not used in this assignment but is present in VideoState.
	   - Rather than a PUT request, a POST is used because, by default, Spring 
	     does not support a PUT with multipart data due to design decisions in the
	     Commons File Upload library: https://issues.apache.org/jira/browse/FILEUPLOAD-197
	     */
	@PostMapping("/video/{id}/data")
	@ResponseBody
	public VideoStatus postVideoData(
			@PathVariable("id") long id, 
			@RequestParam("data") MultipartFile data) {
		Video video = videos.get(id);
		if (video==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "video id not found");
		try {
			VideoFileManager.get().saveVideoData(video, data.getInputStream());
		} catch (IOException e) {
			throw new ResponseStatusException(HttpStatus.NO_CONTENT, "cannot read video");
		}
		return new VideoStatus(VideoState.READY);
	}
	
	/*
	GET /video/{id}/data
	   - Returns the binary mpeg data (if any) for the video with the given
	     identifier. If no mpeg data has been uploaded for the specified video,
	     then the server should return a 404 status code.
	     */ 
	@GetMapping("/video/{id}/data")
	@ResponseBody
	public ResponseEntity<byte[]> getVideoData(@PathVariable("id") long id) {
		Video video = videos.get(id);	
		if (video==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "video id not found");
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			VideoFileManager.get().copyVideoData(video, out);
			return ResponseEntity.ok(out.toByteArray());
		} catch (IOException e) {
			throw new ResponseStatusException(HttpStatus.NO_CONTENT, "cannot read video");
		}
	}

}
