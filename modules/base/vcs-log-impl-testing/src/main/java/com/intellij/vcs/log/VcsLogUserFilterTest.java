/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.vcs.log;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.vcs.log.impl.HashImpl;
import com.intellij.vcs.log.impl.VcsLogFilterCollectionImpl.VcsLogFilterCollectionBuilder;
import com.intellij.vcs.log.impl.VcsLogUserFilterImpl;
import com.intellij.vcs.log.util.UserNameRegex;
import com.intellij.vcs.log.util.VcsUserUtil;
import junit.framework.TestCase;
import javax.annotation.Nonnull;

import java.io.IOException;
import java.util.*;

import static java.util.Collections.*;

public abstract class VcsLogUserFilterTest {
  @Nonnull
  protected final Project myProject;
  @Nonnull
  protected final VcsLogProvider myLogProvider;
  @Nonnull
  protected final VcsLogObjectsFactory myObjectsFactory;

  public VcsLogUserFilterTest(@Nonnull VcsLogProvider logProvider, @Nonnull Project project) {
    myProject = project;
    myLogProvider = logProvider;
    myObjectsFactory = ServiceManager.getService(myProject, VcsLogObjectsFactory.class);
  }

  /*
  Test for IDEA-141382, IDEA-121827, IDEA-141158
   */
  public void testWeirdNames() throws Exception {
    MultiMap<VcsUser, String> commits =
            generateHistory("User [company]", "user@company.com", "Userovich, User", "userovich@company.com", "User (user)",
                            "useruser@company.com");
    List<VcsCommitMetadata> metadata = generateMetadata(commits);

    StringBuilder builder = new StringBuilder();
    for (VcsUser user : commits.keySet()) {
      checkFilterForUser(user, commits.keySet(), commits.get(user), metadata, builder);
    }
    assertFilteredCorrectly(builder);
  }

  public void testWeirdCharacters() throws Exception {
    List<String> names = ContainerUtil.newArrayList();

    for (Character c : UserNameRegex.EXTENDED_REGEX_CHARS) {
      String name = "user" + Character.toString(c) + "userovich" + c.hashCode(); // hashCode is required so that uses wont be synonyms
      names.add(name);
      names.add(name + "@company.com");
    }

    MultiMap<VcsUser, String> commits = generateHistory(ArrayUtil.toStringArray(names));
    List<VcsCommitMetadata> metadata = generateMetadata(commits);

    StringBuilder builder = new StringBuilder();
    for (VcsUser user : commits.keySet()) {
      checkFilterForUser(user, commits.keySet(), commits.get(user), metadata, builder);
    }
    assertFilteredCorrectly(builder);
  }

  public void testFullMatching() throws Exception {
    VcsUser nik = myObjectsFactory.createUser("nik", "nik@company.com");
    List<VcsUser> users = Arrays.asList(nik,
                                        myObjectsFactory.createUser("Chainik", "chainik@company.com"),
                                        myObjectsFactory.createUser("Nik Fury", "nikfury@company.com"),
                                        myObjectsFactory.createUser("nikniknik", "nikniknik@company.com"));

    MultiMap<VcsUser, String> commits = generateHistory(users);
    List<VcsCommitMetadata> metadata = generateMetadata(commits);
    StringBuilder builder = new StringBuilder();
    checkFilterForUser(nik, commits.keySet(), commits.get(nik), metadata, builder);
    assertFilteredCorrectly(builder);
  }

  public void testSynonyms(@Nonnull Set<Character> excludes) throws Exception {
    List<String> names = ContainerUtil.newArrayList();

    Set<String> synonyms = ContainerUtil.newHashSet();
    for (char c = ' '; c <= '~'; c++) {
      if (c == '\'' || c == '!' || c == '\\' || Character.isUpperCase(c) || excludes.contains(c)) continue;
      String name = "User" + Character.toString(c) + "Userovich";
      names.add(name);
      names.add(name + "@company.com");
      if (!Character.isLetterOrDigit(c)) synonyms.add(name);
    }
    names.add("User Userovich Userov");
    names.add("UserUserovich@company.com");

    MultiMap<VcsUser, String> commits = generateHistory(ArrayUtil.toStringArray(names));
    List<VcsCommitMetadata> metadata = generateMetadata(commits);

    List<String> synonymCommits = ContainerUtil.newArrayList();
    for (VcsUser user : commits.keySet()) {
      if (synonyms.contains(user.getName())) synonymCommits.addAll(commits.get(user));
    }

    StringBuilder builder = new StringBuilder();
    for (VcsUser user : commits.keySet()) {
      if (synonyms.contains(user.getName())) {
        checkFilterForUser(user, commits.keySet(), synonymCommits, metadata, builder);
      }
      else {
        checkFilterForUser(user, commits.keySet(), commits.get(user), metadata, builder);
      }
    }
    assertFilteredCorrectly(builder);
  }


  /*
  Turkish character İ corresponds to lower case i, while I is ı.
  But since we ca not find locale by username, this test it incorrect.
  Currently we do not lower-case non-ascii letters at all (works incorrectly for them without the locale), so we do not find synonyms for names with İ and ı.
  And for I and i incorrect synonyms are found (since we assume that I is upper-case for i).
   */
  public void testTurkishLocale() throws Exception {
    VcsUser upperCaseDotUser = myObjectsFactory.createUser("\u0130name", "uppercase.dot@company.com");
    VcsUser lowerCaseDotUser = myObjectsFactory.createUser("\u0069name", "lowercase.dot@company.com");
    VcsUser upperCaseDotlessUser = myObjectsFactory.createUser("\u0049name", "uppercase.dotless@company.com");
    VcsUser lowerCaseDotlessUser = myObjectsFactory.createUser("\u0131name", "lowercase.dotless@company.com");

    List<VcsUser> users = Arrays.asList(upperCaseDotUser, lowerCaseDotUser, upperCaseDotlessUser, lowerCaseDotlessUser);

    MultiMap<VcsUser, String> commits = generateHistory(users);
    List<VcsCommitMetadata> metadata = generateMetadata(commits);

    StringBuilder builder = new StringBuilder();

    checkTurkishAndEnglishLocales(upperCaseDotUser, emptySet(), commits, metadata, builder);
    checkTurkishAndEnglishLocales(lowerCaseDotlessUser, emptySet(), commits, metadata, builder);
    checkTurkishAndEnglishLocales(lowerCaseDotUser, singleton(upperCaseDotlessUser), commits, metadata, builder);
    checkTurkishAndEnglishLocales(upperCaseDotlessUser, singleton(lowerCaseDotUser), commits, metadata, builder);

    assertFilteredCorrectly(builder);
  }

  private void checkTurkishAndEnglishLocales(@Nonnull VcsUser user,
                                             @Nonnull Collection<VcsUser> synonymUsers,
                                             @Nonnull MultiMap<VcsUser, String> commits,
                                             @Nonnull List<VcsCommitMetadata> metadata, @Nonnull StringBuilder builder)
          throws VcsException {
    Set<String> expectedCommits = ContainerUtil.newHashSet(commits.get(user));
    for (VcsUser synonym : synonymUsers) {
      expectedCommits.addAll(commits.get(synonym));
    }

    Locale oldLocale = Locale.getDefault();
    Locale.setDefault(new Locale("tr"));
    StringBuilder turkishBuilder = new StringBuilder();
    checkFilterForUser(user, commits.keySet(), expectedCommits, metadata, turkishBuilder);

    Locale.setDefault(Locale.ENGLISH);
    StringBuilder defaultBuilder = new StringBuilder();
    checkFilterForUser(user, commits.keySet(), expectedCommits, metadata, defaultBuilder);
    Locale.setDefault(oldLocale);

    if (!turkishBuilder.toString().isEmpty()) builder.append("Turkish Locale:\n").append(turkishBuilder);
    if (!defaultBuilder.toString().isEmpty()) builder.append("English Locale:\n").append(defaultBuilder);
  }

  /*
  Test for IDEA-152545
   */
  public void testJeka() throws Exception {
    VcsUser jeka = myObjectsFactory.createUser("User Userovich", "jeka@company.com");
    List<VcsUser> users = Arrays.asList(jeka,
                                        myObjectsFactory.createUser("Auser Auserovich", "auser@company.com"),
                                        myObjectsFactory.createUser("Buser Buserovich", "buser@company.com"),
                                        myObjectsFactory.createUser("Cuser cuserovich", "cuser@company.com"));

    MultiMap<VcsUser, String> commits = generateHistory(users);
    List<VcsCommitMetadata> metadata = generateMetadata(commits);
    StringBuilder builder = new StringBuilder();
    VcsLogUserFilter userFilter = new VcsLogUserFilterImpl(singleton("jeka"), emptyMap(), commits.keySet());
    checkFilter(userFilter, "jeka", commits.get(jeka), metadata, builder);
    assertFilteredCorrectly(builder);
  }

  private void checkFilterForUser(@Nonnull VcsUser user,
                                  @Nonnull Set<VcsUser> allUsers,
                                  @Nonnull Collection<String> expectedHashes,
                                  @Nonnull List<VcsCommitMetadata> metadata, @Nonnull StringBuilder errorMessageBuilder)
          throws VcsException {
    VcsLogUserFilter userFilter = new VcsLogUserFilterImpl(singleton(VcsUserUtil.getShortPresentation(user)), emptyMap(), allUsers);
    checkFilter(userFilter, user.toString(), expectedHashes, metadata, errorMessageBuilder);
  }

  private void checkFilter(VcsLogUserFilter userFilter,
                           String filterDescription,
                           @Nonnull Collection<String> expectedHashes,
                           @Nonnull List<VcsCommitMetadata> metadata, @Nonnull StringBuilder errorMessageBuilder) throws VcsException {
    // filter by vcs
    List<String> actualHashes = getFilteredHashes(userFilter);

    if (!hasSameElements(expectedHashes, actualHashes)) {
      errorMessageBuilder.append(TestCase.format("VCS filter for: " + filterDescription, expectedHashes, actualHashes)).append("\n");
    }

    // filter in memory
    actualHashes = getFilteredHashes(userFilter, metadata);
    if (!hasSameElements(expectedHashes, actualHashes)) {
      errorMessageBuilder.append(TestCase.format("Memory filter for: " + filterDescription, expectedHashes, actualHashes)).append("\n");
    }
  }

  private static <T> boolean hasSameElements(@Nonnull Collection<? extends T> collection, @Nonnull Collection<T> expected) {
    return ContainerUtil.newHashSet(expected).equals(ContainerUtil.newHashSet(collection));
  }

  @Nonnull
  private List<String> getFilteredHashes(@Nonnull VcsLogUserFilter filter) throws VcsException {
    VcsLogFilterCollection filters = new VcsLogFilterCollectionBuilder().with(filter).build();
    List<TimedVcsCommit> commits = myLogProvider.getCommitsMatchingFilter(myProject.getBaseDir(), filters, -1);
    return ContainerUtil.map(commits, commit -> commit.getId().asString());
  }

  @Nonnull
  private static List<String> getFilteredHashes(@Nonnull VcsLogUserFilter filter, @Nonnull List<VcsCommitMetadata> metadata) {
    return ContainerUtil.map(ContainerUtil.filter(metadata, filter::matches), metadata1 -> metadata1.getId().asString());
  }

  @Nonnull
  private List<VcsCommitMetadata> generateMetadata(@Nonnull MultiMap<VcsUser, String> commits) {
    List<VcsCommitMetadata> result = ContainerUtil.newArrayList();

    for (VcsUser user : commits.keySet()) {
      for (String commit : commits.get(user)) {
        result.add(myObjectsFactory.createCommitMetadata(HashImpl.build(commit), emptyList(), System.currentTimeMillis(),
                                                         myProject.getBaseDir(), "subject " + Math.random(), user.getName(),
                                                         user.getEmail(), "message " + Math.random(), user.getName(), user.getEmail(),
                                                         System.currentTimeMillis()));
      }
    }

    return result;
  }

  @Nonnull
  private MultiMap<VcsUser, String> generateHistory(String... names) throws IOException {
    TestCase.assertTrue("Incorrect user names (should be pairs of users and emails) " + Arrays.toString(names), names.length % 2 == 0);

    List<VcsUser> users = ContainerUtil.newArrayList();
    for (int i = 0; i < names.length / 2; i++) {
      users.add(myObjectsFactory.createUser(names[2 * i], names[2 * i + 1]));
    }

    return generateHistory(users);
  }

  @Nonnull
  private MultiMap<VcsUser, String> generateHistory(@Nonnull List<VcsUser> users) throws IOException {
    MultiMap<VcsUser, String> commits = MultiMap.createLinked();

    for (VcsUser user : users) {
      recordCommit(commits, user);
    }

    VfsUtil.markDirtyAndRefresh(false, true, false, myProject.getBaseDir());
    return commits;
  }

  private static void assertFilteredCorrectly(@Nonnull StringBuilder builder) {
    TestCase.assertTrue("Incorrectly filtered log for\n" + builder.toString(), builder.toString().isEmpty());
  }

  private void recordCommit(@Nonnull MultiMap<VcsUser, String> commits, @Nonnull VcsUser user) throws IOException {
    String commit = commit(user);
    commits.putValue(user, commit);
  }

  @Nonnull
  protected abstract String commit(VcsUser user) throws IOException;
}
