package org.jenkinsci.plugins.pitmutation;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import org.apache.commons.digester3.Digester;
import org.jenkinsci.plugins.pitmutation.targets.MutationStats;
import org.xml.sax.SAXException;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

/**
 * @author edward
 */
public class MutationReport implements Serializable {

  private static final Logger logger = Logger.getLogger(MutationReport.class.getName());

  public MutationReport() {
    mutationsByClass_ = HashMultimap.create();
  }

  public static MutationReport create(InputStream xmlReport) throws IOException, SAXException {
    return digestMutations(xmlReport);
  }

  private static MutationReport digestMutations(InputStream input) throws IOException, SAXException {
    Digester digester = new Digester();
    digester.addObjectCreate("mutations", MutationReport.class);
    digester.addObjectCreate("mutations/mutation", Mutation.class);
    digester.addSetNext("mutations/mutation", "addMutation", "org.jenkinsci.plugins.pitmutation.Mutation");
    digester.addSetProperties("mutations/mutation");
    digester.addSetNestedProperties("mutations/mutation");

    MutationReport report = digester.parse(input);
    report.mutationsByPackage_ = Multimaps.index(report.mutationsByClass_.values(), packageIndexFunction);
    return report;
  }

  /**
   * Called by digester.
   *
   * @param mutation
   */
  public void addMutation(Mutation mutation) {
    mutationsByClass_.put(mutation.getMutatedClass(), mutation);
    if (mutation.isDetected()) {
      killCount_++;
    }
    mutationsByPackage_ = Multimaps.index(mutationsByClass_.values(), packageIndexFunction);
  }

  public Collection<Mutation> getMutationsForPackage(String packageName) {
    return mutationsByPackage_.get(packageName);
  }

  public Multimap<String, Mutation> getMutationsByPackage() {
    return mutationsByPackage_;
  }

  public Collection<Mutation> getMutationsForClassName(String className) {
    Collection<Mutation> mutations = mutationsByClass_.get(className);
    return mutations != null ? mutations : EMPTY_SET;
  }

  public MutationStats getMutationStats() {
    return new MutationStats() {
      public String getTitle() {
        return "Report Stats";
      }

      public int getUndetected() {
        return getTotalMutations() - killCount_;
      }

      public int getTotalMutations() {
        return mutationsByClass_.values().size();
      }
    };
  }

  static String packageNameFromClass(String fqcn) {
    int idx = fqcn.lastIndexOf('.');
    return fqcn.substring(0, idx != -1 ? idx : 0);
  }

  public static Predicate<Mutation> isSurvivor_ = new Predicate<Mutation>() {
    public boolean apply(Mutation mutation) {
      return mutation != null && !mutation.isDetected();

    }
  };

  public static final Function<? super Mutation, String> packageIndexFunction = new Function<Mutation, String>() {
    public String apply(Mutation mutation) {
      if (mutation == null) {
        return null;
      }

      return packageNameFromClass(mutation.getMutatedClass());
    }
  };

  public static final Function<? super Mutation,String> classIndexFunction = new Function<Mutation, String>() {
    public String apply(Mutation mutation) {
      if (mutation == null) {
        return null;
      }

      return mutation.getMutatedClass();
    }
  };

  private static final Set<Mutation> EMPTY_SET = new HashSet<>();

  private Multimap<String, Mutation> mutationsByPackage_;
  private Multimap<String, Mutation> mutationsByClass_;
  private int killCount_ = 0;
}
