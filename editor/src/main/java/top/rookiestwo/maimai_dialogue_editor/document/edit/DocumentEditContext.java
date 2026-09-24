package top.rookiestwo.maimai_dialogue_editor.document.edit;

import com.google.gson.JsonObject;
import top.rookiestwo.maimai_dialogue_editor.document.ContentWorkspace;
import top.rookiestwo.maimai_dialogue_editor.project.ProjectDraft;
import top.rookiestwo.maimai_dialogue_editor.resource.InlineResource;
import top.rookiestwo.maimai_dialogue_editor.resource.ResourceKey;
import top.rookiestwo.maimai_dialogue_editor.resource.ResourceTree;
import java.util.Collection;

// 文档编辑只访问当前草稿、选择和提交入口，不持有项目保存或页面控制器。
public interface DocumentEditContext {
    ProjectDraft draft();
    long projectGeneration();
    ContentWorkspace.Snapshot contentSnapshot();
    boolean contentActive();
    ResourceTree.Node resourceSelection();
    ResourceKey openedResource();
    long resourceSelectionRevision();
    Collection<ResourceKey> resourceKeys();
    boolean whenResourceLoaded(ResourceKey key, Runnable ready);
    void beginExtract(InlineResource resource, String suggestedPath);
    void editAsset(ResourceKey key, JsonObject value, String group);
    void endEdit();
}
