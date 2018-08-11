package com.ragedunicorn.tools.maven;

import com.ragedunicorn.tools.maven.model.GitHubApiRelease;
import com.ragedunicorn.tools.maven.model.Release;
import com.ragedunicorn.tools.maven.service.AssetService;
import com.ragedunicorn.tools.maven.service.ReleaseService;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;



@Mojo(name = "rg-github-release")
public class GitHubReleaseMojo extends AbstractMojo {
  // owner of the repository
  @Parameter(property = "owner", required = true)
  private String owner;

  // required - name of the repository
  @Parameter(property = "repository", required = true)
  private String repository;

  // name of the tag - if tag does not exist it will be created
  @Parameter(property = "tagName", required = true)
  private String tagName;

  // determines where the git tag is created from
  @Parameter(property = "targetCommitish", defaultValue = "master")
  private String targetCommitish;

  // the name of the release - will be the name of the tag if left empty
  @Parameter(property = "name")
  private String name;

  // description of the release - content of last commit if left empty
  @Parameter(property = "body")
  private String body;

  // true for creating a draft, false for creating a published one
  @Parameter(property = "draft")
  private Boolean draft;

  // true to identify the release as a prerelease, false to identify as a full release
  @Parameter(property = "prerelease")
  private Boolean prerelease;

  // optional path to a release notes file - will override body
  @Parameter(property = "releaseNotes")
  private String releaseNotes;

  @Parameter(property = "assets")
  private String[] assets;

  @Parameter(property = "authToken")
  private String authToken;

  @Parameter(property = "server")
  private String server;

  @Parameter(defaultValue = "${settings}", readonly = true)
  private Settings settings;

  @Parameter(defaultValue = "${project}", readonly = true)
  private MavenProject project;

  @Parameter(defaultValue = "${mojoExecution}", readonly = true)
  private MojoExecution execution;

  /**
   * Plugin execution callback.
   *
   * @throws MojoExecutionException If any exception happens during the execution of the plugin
   */
  public void execute() throws MojoExecutionException {
    validateRequiredInputParameters();

    GitHubClient gitHubClient = createGitHubClient();
    GitHubApiRelease releaseResponse = createRelease(gitHubClient);
    uploadAssets(gitHubClient, releaseResponse.getUploadUrl());
  }

  /**
   * Create a new release on GitHub.
   *
   * @param gitHubClient The GitHub client
   * @return The response object of the create release endpoint
   * @throws MojoExecutionException If any exception happens during the execution of the release
   *     service
   */
  private GitHubApiRelease createRelease(GitHubClient gitHubClient) throws MojoExecutionException {
    final ReleaseService releaseService = new ReleaseService(gitHubClient);
    Release release = new Release();

    release.setTagName(tagName);
    release.setTargetCommitish(targetCommitish);
    release.setName(name);
    release.setBody(body);
    release.setReleaseNotes(releaseNotes);
    release.setDraft(draft);
    release.setPrerelease(prerelease);

    return releaseService.createReleaseOperation(release);
  }

  /**
   * Upload asset(s) to previously created GitHub release.
   *
   * @param gitHubClient The GitHub client
   * @param uploadUrl The url to use to upload the assets
   * @throws MojoExecutionException If any exception happens during the execution of the asset
   *     service
   */
  private void uploadAssets(GitHubClient gitHubClient, String uploadUrl)
      throws MojoExecutionException {
    AssetService assetService = new AssetService(gitHubClient);
    assetService.uploadAssetsOperation(uploadUrl, assets);
  }

  /**
   * Retrieve the auth token for the Github Api.
   *
   * @return A property object containing the Github Api token
   * @throws MojoExecutionException An exception occurring during the execution of a plugin
   */
  private String getCredentials() throws MojoExecutionException {
    // prefer settings parameter over direct configuration in pom
    if (settings != null && server != null) {
      final Server serverEntry = settings.getServer(server);
      if (serverEntry != null) {
        authToken = serverEntry.getPassphrase();

        if (authToken == null || authToken.isEmpty()) {
          throw new MojoExecutionException("Found server entry in settings.xml "
              + "but authToken parameter was missing or is empty");
        }
      } else {
        getLog().warn("Unable to retrieve settings or server. Falling back to project settings");
      }
    }
    // fallback to plugin configuration if credentials cannot be retrieved from maven settings.xml
    if (authToken == null) {
      throw new MojoExecutionException("Unable to read authentication configuration make "
          + "sure to set the authToken property");
    }

    return authToken;
  }

  /**
   * Validate required input parameters.
   *
   * @throws MojoExecutionException An exception occurring during the execution of a plugin
   */
  private void validateRequiredInputParameters() throws MojoExecutionException {
    if (owner == null || owner.isEmpty()) {
      throw new MojoExecutionException("Missing required parameter owner");
    }

    if (repository == null || repository.isEmpty()) {
      throw new MojoExecutionException("Missing required parameter repository");
    }
  }

  /**
   * Create a new Github client and set its owner and the targeted repository.
   * Additionally the authToken for authenticating against the GitHub Api is set.
   *
   * @return The created GitHub client
   */
  private GitHubClient createGitHubClient() throws MojoExecutionException {
    GitHubClient gitHubClient = new GitHubClient();
    gitHubClient.setOAuthToken(getCredentials());
    gitHubClient.setRepository(repository);
    gitHubClient.setOwner(owner);

    return gitHubClient;
  }
}
