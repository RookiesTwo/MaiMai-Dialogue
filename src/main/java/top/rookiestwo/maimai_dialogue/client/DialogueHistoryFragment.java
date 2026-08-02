package top.rookiestwo.maimai_dialogue.client;

import icyllis.modernui.annotation.NonNull;
import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.util.DataSet;
import icyllis.modernui.view.LayoutInflater;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import top.rookiestwo.maimai_dialogue.theme.ThemeDefinition;

import javax.annotation.Nullable;

/**
 * Full-screen history page hosted by the dialogue screen's back stack.
 */
public final class DialogueHistoryFragment extends Fragment {
    public DialogueHistoryFragment() {
    }

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable DataSet savedInstanceState
    ) {
        ClientDialogueController controller = ClientServices.get().dialogues();
        DialogueHistoryView history = new DialogueHistoryView(
                requireContext(),
                () -> getParentFragmentManager().popBackStack()
        );
        var state = controller.viewState();
        history.applyTheme(state.theme().orElse(ThemeDefinition.DEFAULT));
        history.render(state.history());
        history.showLatest();
        return history;
    }

    @Override
    public void onDestroyView() {
        for (Fragment fragment : getParentFragmentManager().getFragments()) {
            if (fragment instanceof DialogueFragment dialogue) {
                dialogue.onHistoryClosed();
                break;
            }
        }
        super.onDestroyView();
    }
}
