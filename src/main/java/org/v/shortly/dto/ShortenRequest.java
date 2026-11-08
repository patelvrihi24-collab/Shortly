package org.v.shortly.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
public record ShortenRequest( //a record: an immutable data holder ; Java generates the constructor and getters
                              @NotBlank(message = "url must not be empty") //reject null, "" and " "
                              @Size(max = 2048, message = "url must be at most 2048 characters")// stops absurdly huge input
                              @Pattern(regexp = "^https?://\\S+$",
                                      flags = Pattern.Flag.CASE_INSENSITIVE,
                                      message = "url must start with http:// or https:// and contain no space")
                              String url          // the single field: JSON {"url": "..."} maps to this
){}
