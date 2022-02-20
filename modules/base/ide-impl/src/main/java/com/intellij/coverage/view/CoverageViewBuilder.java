package com.intellij.coverage.view;

import com.intellij.ide.commander.AbstractListBuilder;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import consulo.ui.ex.tree.AbstractTreeStructure;
import com.intellij.ide.util.treeView.AlphaComparator;
import consulo.application.progress.ProgressIndicator;
import consulo.application.progress.ProgressManager;
import consulo.application.progress.Task;
import consulo.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vcs.FileStatusListener;
import com.intellij.openapi.vcs.FileStatusManager;
import consulo.virtualFileSystem.VirtualFile;
import com.intellij.ui.TableUtil;
import consulo.ui.ex.table.JBTable;
import javax.annotation.Nonnull;
import consulo.ui.annotation.RequiredUIAccess;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/**
 * User: anna
 * Date: 1/2/12
 */
public class CoverageViewBuilder extends AbstractListBuilder {
  private final JBTable myTable;
  private final FileStatusListener myFileStatusListener;
  private CoverageViewExtension myCoverageViewExtension;

  CoverageViewBuilder(final Project project,
                      final JList list,
                      final Model model,
                      final AbstractTreeStructure treeStructure, final JBTable table) {
    super(project, list, model, treeStructure, AlphaComparator.INSTANCE, false);
    myTable = table;
    ProgressManager.getInstance().run(new Task.Backgroundable(project, "Building coverage report...") {
      @Override
      public void run(@Nonnull ProgressIndicator indicator) {
        buildRoot();
      }

      @RequiredUIAccess
      @Override
      public void onSuccess() {
        ensureSelectionExist();
        updateParentTitle();
      }
    });
    myFileStatusListener = new FileStatusListener() {
      @Override
      public void fileStatusesChanged() {
        table.repaint();
      }

      @Override
      public void fileStatusChanged(@Nonnull VirtualFile virtualFile) {
        table.repaint();
      }
    };
    myCoverageViewExtension = ((CoverageViewTreeStructure)myTreeStructure).myData
      .getCoverageEngine().createCoverageViewExtension(myProject, ((CoverageViewTreeStructure)myTreeStructure).myData,
                                                       ((CoverageViewTreeStructure)myTreeStructure).myStateBean);
    FileStatusManager.getInstance(myProject).addFileStatusListener(myFileStatusListener);
  }

  @Override
  public void dispose() {
    FileStatusManager.getInstance(myProject).removeFileStatusListener(myFileStatusListener);
    super.dispose();
  }

  @Override
  protected boolean shouldEnterSingleTopLevelElement(Object rootChild) {
    return false;
  }

  @Override
  protected boolean shouldAddTopElement() {
    return false;
  }

  @Override
  protected boolean nodeIsAcceptableForElement(AbstractTreeNode node, Object element) {
    return Comparing.equal(node.getValue(), element);
  }

  @Override
  protected List<AbstractTreeNode> getAllAcceptableNodes(Object[] childElements, VirtualFile file) {
    ArrayList<AbstractTreeNode> result = new ArrayList<AbstractTreeNode>();

    for (Object childElement1 : childElements) {
      CoverageListNode childElement = (CoverageListNode)childElement1;
      if (childElement.contains(file)) result.add(childElement);
    }

    return result;
  }

  @Override
  protected void updateParentTitle() {
    if (myParentTitle == null) return;

    final Object rootElement = myTreeStructure.getRootElement();
    AbstractTreeNode node = getParentNode();
    if (node == null) {
      node = (AbstractTreeNode)rootElement;
    }

    if (node instanceof CoverageListRootNode) {
      myParentTitle.setText(myCoverageViewExtension.getSummaryForRootNode(node));
    }
    else {
      myParentTitle.setText(myCoverageViewExtension.getSummaryForNode(node));
    }
  }

  @Override
  public  Object getSelectedValue() {
    final int row = myTable.getSelectedRow();
    if (row == -1) return null;
    return myModel.getElementAt(myTable.convertRowIndexToModel(row));
  }

  @Override
  protected void ensureSelectionExist() {
    TableUtil.ensureSelectionExists(myTable);
  }

  @Override
  protected void selectItem(int i) {
    TableUtil.selectRows(myTable, new int[]{myTable.convertRowIndexToView(i)});
    TableUtil.scrollSelectionToVisible(myTable);
  }

  public boolean canSelect(VirtualFile file) {
    return myCoverageViewExtension.canSelectInCoverageView(file);
  }

  public void select(Object object) {
    selectElement(myCoverageViewExtension.getElementToSelect(object), myCoverageViewExtension.getVirtualFile(object));
  }
}
