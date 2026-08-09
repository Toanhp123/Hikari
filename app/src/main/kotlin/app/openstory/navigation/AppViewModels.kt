package app.openstory.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner

internal fun <T : ViewModel> appViewModel(
    owner: ViewModelStoreOwner,
    key: String,
    modelClass: Class<T>,
    create: () -> T,
): T = ViewModelProvider(
    owner,
    LambdaViewModelFactory(
        modelClass = modelClass,
        create = create,
    ),
).get(key, modelClass)

private class LambdaViewModelFactory<T : ViewModel>(
    private val modelClass: Class<T>,
    private val create: () -> T,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <R : ViewModel> create(
        modelClass: Class<R>,
    ): R {
        require(modelClass == this.modelClass) {
            "Unexpected ViewModel class: ${modelClass.name}"
        }
        return create() as R
    }
}
